# Configuración de Firebase – Meeple Kingdom

1. Firebase Console → **Agregar proyecto** → registrar app Android con el paquete exacto `com.example.meeplekingdom`.
2. Descargar `google-services.json` y copiarlo en `app/google-services.json`.
3. **Authentication** → Método de acceso → habilitar *Correo electrónico/contraseña*.
4. **Firestore Database** → Crear base de datos → pegar `firestore.rules` en la pestaña *Reglas* → Publicar.
5. **Storage** (requiere plan Blaze) → Comenzar → pegar `storage.rules` en *Reglas* → Publicar.
6. **Primer administrador**: registra una cuenta desde la app y, en Firestore, edita su documento
   `users/{uid}` cambiando el campo `rol` a `admin`. (Con las reglas, nadie puede auto-asignarse ese rol.)
7. Compilar en Android Studio (*Sync Project with Gradle Files* → *Run*). Para probar la huella usa un dispositivo físico.

## Notas
- Eliminar un usuario desde la app borra su perfil de Firestore; su cuenta de Authentication queda sin acceso
  porque el login exige perfil activo. El borrado total de la cuenta requiere Admin SDK / Cloud Functions.
- La huella cifra la contraseña con una clave del Android Keystore ligada a la biometría (no se guarda en claro).

## Fase 4 – Carrito y pedidos
- **Vuelve a publicar `firestore.rules`**: ahora incluyen carrito (`users/{uid}/cart`), pedidos (`orders`) y la regla
  que permite al comprador solo *descontar* stock al confirmar una compra.
- Cada compra crea **un pedido por vendedor** (mismo `grupoId`) dentro de una transacción: valida stock, descuenta
  inventario, crea los pedidos y vacía el carrito. Si algo falla no se escribe nada.
- Estados: `pendiente_pago` → `pagado` → `enviado` → `entregado` (o `cancelado`, que devuelve el stock).
  El paso a `pagado` lo hará la pasarela ePayco en la fase 5; mientras tanto puedes cambiar `estado` a mano en la
  consola de Firestore para probar los botones del vendedor.

### Prueba rápida
1. Vendedor: publica 2 productos con foto.  2. Comprador: Catálogo → Detalle → Añadir al carrito → Ir a pagar → Confirmar pedido.
3. Comprador: pestaña Pedidos (estado *Pendiente de pago*).  4. Vendedor: Pedidos recibidos y panel con métricas.

## Fase 5 – Geolocalización y pagos con ePayco

### Por qué hay un backend (carpeta `functions/`)
El Smart Checkout de ePayco exige crear la sesión de pago con la **PUBLIC_KEY y la PRIVATE_KEY**. La llave privada
no puede ir dentro del APK, así que vive como *secreto* en Cloud Functions. Funciones incluidas:
- `createPaymentSession` (la app la llama): calcula el monto desde Firestore y crea la sesión en ePayco.
- `checkoutPage` / `paymentResult`: páginas que se muestran en el WebView de la app.
- `epaycoConfirmation`: webhook de ePayco. Valida la firma `x_signature` y el monto y **solo aquí** el pedido pasa a `pagado`.

### Despliegue (una sola vez)
1. Instala Node 22 y `npm install -g firebase-tools`; luego `firebase login` y, en la raíz del proyecto, `firebase use --add` (elige tu proyecto).
2. En el panel de ePayco (dashboard.epayco.com → Configuración) toma tu **PUBLIC_KEY**, **PRIVATE_KEY**, **ID de cliente (P_CUST_ID_CLIENTE)** y **P_KEY**.
3. Guarda los secretos (la consola te pedirá pegar el valor de cada uno):
   ```
   firebase functions:secrets:set EPAYCO_PUBLIC_KEY
   firebase functions:secrets:set EPAYCO_PRIVATE_KEY
   firebase functions:secrets:set EPAYCO_CUSTOMER_ID
   firebase functions:secrets:set EPAYCO_P_KEY
   ```
4. `cd functions && npm install && cp .env.example .env` (deja `EPAYCO_TEST=true` mientras pruebas).
5. Desde la raíz: `firebase deploy --only functions,firestore:rules,storage`. La primera vez te pedirá habilitar APIs (Cloud Build, Artifact Registry, Secret Manager): acepta.
6. Si cambias de región, edita `REGION` en `functions/index.js` **y** en `data/PaymentRepository.kt`.

### Probar el pago
1. Comprador: Carrito → Ir a pagar → **Usar mi ubicación actual** (acepta el permiso; en dispositivo físico con GPS) → *Confirmar y pagar con ePayco*.
2. Paga con los datos de prueba de la documentación de ePayco (docs.epayco.com → "Tablas de datos"/datos de prueba).
3. Al cerrar el checkout la app espera hasta 20 s la confirmación del webhook y muestra *¡Pago confirmado!*; el pedido pasa a **Pagado**.
4. Vendedor: Pedidos recibidos → *Marcar enviado* → *Marcar entregado* (y *Ver en mapa* abre la dirección de entrega).
5. Administrador: Reporte de Ventas (ventas totales, ticket promedio, ventas por vendedor, cancelar pedidos abiertos).

### Si algo falla
- *"El servicio de pagos no está disponible"*: las funciones no están desplegadas o la región no coincide.
- *"No se pudo autenticar con ePayco"*: revisa PUBLIC_KEY / PRIVATE_KEY (`firebase functions:log`).
- El checkout no se dibuja dentro del WebView: en `functions/index.js` cambia `type: 'onpage'` por `type: 'standard'` y vuelve a desplegar (ePayco redirige a `paymentResult`, que cierra el WebView).
- El pedido no pasa a *Pagado*: mira `firebase functions:log` (firma o monto inválidos) y verifica que ePayco pueda alcanzar la URL `.../epaycoConfirmation`.
- Los pedidos abandonados en *pendiente de pago* retienen stock hasta que el vendedor o el administrador los cancele.
