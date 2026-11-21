/**
 * Cloud Functions de Meeple Kingdom: pagos con ePayco Smart Checkout (v2).
 *
 * Por qué existe un backend: ePayco exige crear la sesión de pago con la PUBLIC_KEY y la PRIVATE_KEY.
 * La llave privada jamás debe ir dentro del APK, así que vive aquí como secreto de Firebase.
 *
 * Flujo:
 *  1. La app llama a `createPaymentSession` con el grupoId de sus pedidos pendientes.
 *  2. La función calcula el monto DESDE FIRESTORE (no confía en el cliente), crea la sesión en ePayco
 *     y devuelve la URL de `checkoutPage`, que la app abre en un WebView.
 *  3. ePayco avisa el resultado a `epaycoConfirmation` (webhook servidor a servidor). Allí se valida la
 *     firma x_signature y el monto, y recién entonces los pedidos pasan a "pagado".
 *  4. La app escucha Firestore y muestra la confirmación cuando los pedidos cambian de estado.
 */
const functions = require('firebase-functions/v1');
const admin = require('firebase-admin');
const crypto = require('crypto');

admin.initializeApp();
const db = admin.firestore();

const REGION = 'us-central1';
// Secretos: se cargan con `firebase functions:secrets:set NOMBRE` (ver FIREBASE_SETUP.md).
const SECRETS = ['EPAYCO_PUBLIC_KEY', 'EPAYCO_PRIVATE_KEY', 'EPAYCO_CUSTOMER_ID', 'EPAYCO_P_KEY'];
// Modo pruebas de ePayco; se define en functions/.env (EPAYCO_TEST=true|false).
const TEST = process.env.EPAYCO_TEST !== 'false';
const APIFY = 'https://apify.epayco.co';

/** Devuelve el id del proyecto de Firebase donde corre la función. */
function projectId() {
  if (process.env.GCLOUD_PROJECT) return process.env.GCLOUD_PROJECT;
  return JSON.parse(process.env.FIREBASE_CONFIG || '{}').projectId;
}

/** URL base pública de las funciones HTTP de este proyecto. */
function baseUrl() {
  return `https://${REGION}-${projectId()}.cloudfunctions.net`;
}

/** Suma el total de una lista de documentos de pedidos y lo redondea a pesos enteros. */
function totalDe(docs) {
  return Math.round(docs.reduce((suma, d) => suma + Number(d.get('total') || 0), 0));
}

/**
 * Callable: crea una sesión de ePayco para los pedidos pendientes de pago de un grupo.
 * Entrada: { grupoId }.  Salida: { sessionId, checkoutUrl, monto }.
 */
exports.createPaymentSession = functions
  .region(REGION)
  .runWith({ secrets: SECRETS })
  .https.onCall(async (data, context) => {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'Debes iniciar sesión para pagar.');
    }
    const grupoId = String((data && data.grupoId) || '');
    if (!/^[A-Za-z0-9]{10,40}$/.test(grupoId)) {
      throw new functions.https.HttpsError('invalid-argument', 'Identificador de pedido inválido.');
    }

    // Solo pedidos del propio comprador y que sigan pendientes de pago.
    const snap = await db.collection('orders')
      .where('compradorId', '==', context.auth.uid)
      .where('grupoId', '==', grupoId)
      .get();
    const pendientes = snap.docs.filter((d) => d.get('estado') === 'pendiente_pago');
    if (pendientes.length === 0) {
      throw new functions.https.HttpsError('failed-precondition', 'No hay pedidos pendientes de pago.');
    }
    const monto = totalDe(pendientes);
    if (monto <= 0) {
      throw new functions.https.HttpsError('failed-precondition', 'El monto del pedido es inválido.');
    }

    // 1) Login en Apify de ePayco (Basic PUBLIC_KEY:PRIVATE_KEY) para obtener un token.
    const basic = Buffer.from(
      `${process.env.EPAYCO_PUBLIC_KEY}:${process.env.EPAYCO_PRIVATE_KEY}`
    ).toString('base64');
    const login = await fetch(`${APIFY}/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Basic ${basic}` },
    });
    const loginJson = await login.json().catch(() => ({}));
    if (!login.ok || !loginJson.token) {
      console.error('Login ePayco falló', login.status, JSON.stringify(loginJson));
      throw new functions.https.HttpsError('internal', 'No se pudo autenticar con ePayco. Revisa las llaves.');
    }

    // 2) Crear la sesión de Smart Checkout. grupoId viaja en extra1 para identificarlo en el webhook.
    const sesion = await fetch(`${APIFY}/payment/session/create`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${loginJson.token}` },
      body: JSON.stringify({
        checkout_version: '2',
        name: 'Meeple Kingdom',
        description: `Pedido Meeple Kingdom #${grupoId.slice(-6).toUpperCase()}`,
        currency: 'COP',
        amount: monto,
        lang: 'ES',
        country: 'CO',
        invoice: `${grupoId}-${Date.now().toString(36)}`,
        method: 'POST',
        response: `${baseUrl()}/paymentResult`,
        confirmation: `${baseUrl()}/epaycoConfirmation`,
        extras: { extra1: grupoId, extra2: context.auth.uid },
      }),
    });
    const sesionJson = await sesion.json().catch(() => ({}));
    const sessionId = sesionJson && sesionJson.data && sesionJson.data.sessionId;
    if (!sesion.ok || !sessionId) {
      console.error('Crear sesión ePayco falló', sesion.status, JSON.stringify(sesionJson));
      throw new functions.https.HttpsError('internal', 'ePayco no pudo crear la sesión de pago.');
    }

    return {
      sessionId,
      monto,
      checkoutUrl: `${baseUrl()}/checkoutPage?sessionId=${encodeURIComponent(sessionId)}`,
    };
  });

/** HTML de la página que abre el Smart Checkout de ePayco (se muestra dentro del WebView de la app). */
function checkoutHtml(sessionId) {
  return `<!DOCTYPE html>
<html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Pago - Meeple Kingdom</title>
<style>
  body{font-family:sans-serif;background:#FFF8E7;margin:0;padding:24px;text-align:center;color:#1A237E}
  button{background:#1A237E;color:#fff;border:0;border-radius:12px;padding:14px 24px;font-size:16px}
</style>
<script src="https://checkout.epayco.co/checkout-v2.js"></script>
</head><body>
<h3>Meeple Kingdom</h3>
<p id="estado">Abriendo la pasarela de pagos…</p>
<button id="reabrir" style="display:none">Reintentar</button>
<script>
(function () {
  var sessionId = ${JSON.stringify(sessionId)};
  var estado = document.getElementById('estado');
  var boton = document.getElementById('reabrir');
  // Avisa a la app (puente MeepleBridge) que el checkout terminó; la app verifica el pago en Firestore.
  function terminar() {
    if (window.MeepleBridge && window.MeepleBridge.onFinished) { window.MeepleBridge.onFinished(); }
  }
  function abrir() {
    try {
      var checkout = ePayco.checkout.configure({ sessionId: sessionId, type: 'onpage', test: ${TEST} });
      checkout.setHooks({
        onCreated: function () { estado.textContent = 'Completa tu pago en la ventana de ePayco.'; },
        onResponse: function () { estado.textContent = 'Pago procesado. Confirmando…'; },
        onErrors: function () { estado.textContent = 'No se pudo procesar el pago.'; boton.style.display = 'inline-block'; },
        onClosed: function () { terminar(); }
      });
      checkout.open();
    } catch (e) {
      estado.textContent = 'No se pudo abrir la pasarela de pagos.';
      boton.style.display = 'inline-block';
    }
  }
  boton.onclick = function () { boton.style.display = 'none'; abrir(); };
  window.addEventListener('load', abrir);
})();
</script>
</body></html>`;
}

/** HTTP GET: página con el checkout de ePayco. Parámetro: ?sessionId=... */
exports.checkoutPage = functions.region(REGION).https.onRequest((req, res) => {
  const sessionId = String(req.query.sessionId || '');
  // Solo caracteres alfanuméricos: evita inyectar código en la página.
  if (!/^[A-Za-z0-9]{10,64}$/.test(sessionId)) {
    res.status(400).send('Sesión de pago inválida');
    return;
  }
  res.set('Content-Type', 'text/html; charset=utf-8');
  res.set('Cache-Control', 'no-store');
  res.status(200).send(checkoutHtml(sessionId));
});

/** HTTP GET: página de respuesta a la que ePayco redirige al usuario; cierra el WebView de la app. */
exports.paymentResult = functions.region(REGION).https.onRequest((req, res) => {
  res.set('Content-Type', 'text/html; charset=utf-8');
  res.set('Cache-Control', 'no-store');
  res.status(200).send(`<!DOCTYPE html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Meeple Kingdom</title></head>
<body style="font-family:sans-serif;background:#FFF8E7;color:#1A237E;text-align:center;padding:32px">
<h3>Estamos confirmando tu pago…</h3><p>Puedes volver a la aplicación.</p>
<script>setTimeout(function(){ if (window.MeepleBridge && window.MeepleBridge.onFinished) { window.MeepleBridge.onFinished(); } }, 1500);</script>
</body></html>`);
});

/**
 * Webhook de ePayco (servidor a servidor). Es la ÚNICA vía que marca pedidos como pagados.
 * Valida la firma x_signature = SHA256(cust_id^p_key^ref_payco^transaction_id^amount^currency),
 * comprueba que el monto coincida con lo que se debe y es idempotente (ePayco puede reintentar).
 */
exports.epaycoConfirmation = functions
  .region(REGION)
  .runWith({ secrets: SECRETS })
  .https.onRequest(async (req, res) => {
    try {
      const d = Object.assign({}, req.query || {}, req.body || {});

      const firma = crypto
        .createHash('sha256')
        .update(
          `${process.env.EPAYCO_CUSTOMER_ID}^${process.env.EPAYCO_P_KEY}^${d.x_ref_payco}^${d.x_transaction_id}^${d.x_amount}^${d.x_currency_code}`
        )
        .digest('hex');
      if (firma !== d.x_signature) {
        console.error('Firma inválida: posible intento de fraude', d.x_ref_payco);
        res.status(400).send('Invalid signature');
        return;
      }

      const grupoId = String(d.x_extra1 || String(d.x_id_invoice || '').split('-')[0]);
      const refPayco = String(d.x_ref_payco || '');
      if (!/^[A-Za-z0-9]{10,40}$/.test(grupoId) || !refPayco) {
        res.status(200).send('OK');
        return;
      }

      // Bitácora de pagos para auditoría (solo accesible desde el backend).
      await db.collection('payments').doc(refPayco).set({
        grupoId,
        refPayco,
        transaccion: String(d.x_transaction_id || ''),
        respuesta: String(d.x_response || ''),
        motivo: String(d.x_response_reason_text || ''),
        monto: Number(d.x_amount || 0),
        moneda: String(d.x_currency_code || ''),
        franquicia: String(d.x_franchise || ''),
        prueba: String(d.x_test_request || ''),
        recibidoEn: Date.now(),
      }, { merge: true });

      if (d.x_response === 'Aceptada') {
        const snap = await db.collection('orders').where('grupoId', '==', grupoId).get();
        const pendientes = snap.docs.filter((o) => o.get('estado') === 'pendiente_pago');
        // Si no hay pendientes, ya se procesó antes (reintento del webhook): no se hace nada.
        if (pendientes.length > 0) {
          const esperado = totalDe(pendientes);
          if (String(d.x_currency_code).toUpperCase() !== 'COP' || Math.abs(Number(d.x_amount) - esperado) > 1) {
            console.error('Monto/moneda no coinciden', { grupoId, recibido: d.x_amount, esperado });
          } else {
            const lote = db.batch();
            pendientes.forEach((o) => lote.update(o.ref, {
              estado: 'pagado',
              metodoPago: String(d.x_franchise || 'ePayco'),
              pagoRef: refPayco,
              pagadoEn: Date.now(),
            }));
            await lote.commit();
          }
        }
      }
      res.status(200).send('OK');
    } catch (error) {
      console.error('Error procesando el webhook de ePayco', error);
      res.status(500).send('Internal Server Error');
    }
  });
