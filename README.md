# Meeple Kingdom 

Aplicación Android nativa (Frontend) para compra, venta, intercambio y subasta de juegos de mesa.

## Funcionalidades Implementadas
*   **Transversal:** Splash screen, Login, Biometría real con `BiometricPrompt`, Recuperación de contraseña, estilos centralizados (`colors.xml`, `themes.xml`).
*   **Comprador:** Navegación con `Fragments` (Bottom Nav), Inicio, Detalle de producto, Carrito, Pasarela de Pagos y Perfil.
*   **Vendedor:** Panel con métricas, CRUD dinámico de productos (agregar/eliminar visualmente), historial de pedidos y perfil de tienda.
*   **Administrador:** Dashboard, Gestión de usuarios (CRUD visual), Moderación de productos y Reporte de ventas.

## Simulación de Roles (Para pruebas)
En el Login, ingresa los correos (la contraseña es indistinta):
*   `admin@meeple.com` -> Administrador
*   `vendedor@meeple.com` -> Vendedor
*   Cualquier otro correo o botón de huella -> Comprador