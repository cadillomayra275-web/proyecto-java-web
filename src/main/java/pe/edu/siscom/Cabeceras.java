package pe.edu.siscom;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;

public final class Cabeceras implements Filter {
    public void init(FilterConfig configuracion) { }
    public void destroy() { }
    public void doFilter(ServletRequest peticion, ServletResponse respuesta, FilterChain cadena) throws IOException, ServletException {
        HttpServletResponse salida = (HttpServletResponse) respuesta;
        salida.setHeader("X-Content-Type-Options", "nosniff");
        salida.setHeader("X-Frame-Options", "DENY");
        salida.setHeader("Referrer-Policy", "no-referrer");
        salida.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        cadena.doFilter(peticion, respuesta);
    }
}
