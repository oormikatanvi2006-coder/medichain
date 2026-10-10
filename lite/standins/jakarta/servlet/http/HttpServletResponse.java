package jakarta.servlet.http; public interface HttpServletResponse { void setStatus(int s); void setContentType(String t); java.io.PrintWriter getWriter() throws java.io.IOException; }
