package jakarta.servlet.http; public interface HttpServletRequest { String getMethod(); String getRequestURI(); String getHeader(String n); String getParameter(String n); }
