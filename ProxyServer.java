import java.io.*;
import java.net.*;

public class ProxyServer {
    public static void main(String[] args) throws IOException {
        int port = 8888;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Proxy running on port " + port);
        
        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(() -> handleClient(clientSocket)).start();
        }
    }
    
    private static void handleClient(Socket clientSocket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();
            
            String requestLine = in.readLine();
            if (requestLine == null) return;
            
            System.out.println("Received: " + requestLine);
            
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            
            String method = parts[0];
            String urlString = parts[1];
            
            String path;
            if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
                URL url = new URL(urlString);
                path = url.getFile();
            } else {
                path = urlString;
            }
            
            if (path == null || path.isEmpty()) path = "/";
            
            URL localUrl = new URL("http://127.0.0.1:8080" + path);
            System.out.println("Forwarding to: " + localUrl);
            
            HttpURLConnection conn = (HttpURLConnection) localUrl.openConnection();
            conn.setRequestMethod(method);
            conn.setDoOutput(method.equals("POST") || method.equals("PUT"));
            
            int contentLength = 0;
            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                String[] headerParts = headerLine.split(": ", 2);
                if (headerParts.length == 2) {
                    String key = headerParts[0];
                    String value = headerParts[1];
                    if (!key.equalsIgnoreCase("Host") && !key.equalsIgnoreCase("Connection")) {
                        conn.setRequestProperty(key, value);
                    }
                    if (key.equalsIgnoreCase("Content-Length")) {
                        contentLength = Integer.parseInt(value);
                    }
                }
            }
            
            if (contentLength > 0) {
                byte[] bodyBytes = new byte[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int r = clientSocket.getInputStream().read(bodyBytes, read, contentLength - read);
                    if (r == -1) break;
                    read += r;
                }
                OutputStream connOut = conn.getOutputStream();
                connOut.write(bodyBytes, 0, read);
                connOut.flush();
            }
            
            int responseCode = conn.getResponseCode();
            out.write(("HTTP/1.1 " + responseCode + " " + conn.getResponseMessage() + "\r\n").getBytes());
            
            for (int i = 1; ; i++) {
                String headerKey = conn.getHeaderFieldKey(i);
                String headerValue = conn.getHeaderField(i);
                if (headerKey == null && headerValue == null) break;
                if (headerKey != null) {
                    out.write((headerKey + ": " + headerValue + "\r\n").getBytes());
                }
            }
            out.write("\r\n".getBytes());
            
            InputStream connIn = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (connIn != null) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = connIn.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            out.flush();
            clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
