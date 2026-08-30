SISCOM - Sistema Web de Ventas e Inventario

Tecnologías: Java 8, Java EE 7, JSF 2.2, JSP 2.3, Servlet 3.1, JDBC, MySQL, Bootstrap, JavaScript y CSS.
Proyecto sin Maven. Preparado como base académica para NetBeans 8.2 + GlassFish 4.1.1.

1. Crear la base de datos ejecutando sql/siscom_db.sql.
2. Configurar usuario y contraseña de MySQL en Conexion.java.
3. Crear un proyecto Web Application en NetBeans sin Maven y copiar src/java y web.
4. Agregar MySQL Connector/J al proyecto.
5. Seleccionar GlassFish Server 4.1.1 y Java 8.
6. Ejecutar y abrir /SISCOM_Web_JavaEE/.

Usuario de demostración creado por el script SQL: admin@siscom.pe / admin123
Para producción se debe reemplazar el almacenamiento de contraseña por BCrypt o PBKDF2.
