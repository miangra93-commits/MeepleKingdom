# Meeple Kingdom 🎲

Aplicación Android nativa para la compra, venta, intercambio y subasta de juegos de mesa. Este proyecto corresponde al desarrollo del **Frontend (Actividad 3)**, implementando navegación por roles, layouts personalizados y recursos centralizados.

## Tecnologías utilizadas
*   **Lenguaje:** Kotlin
*   **UI:** XML, Material Design 3, CardView, ScrollView.
*   **Arquitectura:** Activity + Fragments.
*   **Recursos:** Estilos y colores centralizados en `values/` y formas en `drawable/`.

## Roles y Funcionalidades
1.  **Comprador:** Navegación por BottomNavigation (Inicio con catálogo de subastas/tiendas, Carrito, Pedidos, Perfil).
2.  **Vendedor:** Panel con métricas de ventas y lista de productos.
3.  **Administrador:** Dashboard de control de usuarios y moderación.

## Simulación de Roles (Para pruebas)
En la pantalla de Login, ingresa los siguientes correos (la contraseña es indistinta):
*   `admin@meeple.com` -> Inicia sesión como **Administrador**.
*   `vendedor@meeple.com` -> Inicia sesión como **Vendedor**.
*   Cualquier otro correo o el botón de huella -> Inicia sesión como **Comprador**.