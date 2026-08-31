import java.io.*;
import java.net.*;
import java.nio.file.*;

public class FileServer {
    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(8000);
        System.out.println("Serving APK on port 8000");
        while(true) {
            Socket client = server.accept();
            new Thread(() -> {
                try {
                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    String line = in.readLine();
                    if(line != null && line.startsWith("GET")) {
                        OutputStream out = client.getOutputStream();
                        File file = new File("C:\\Users\\ychan\\.gemini\\antigravity\\brain\\8d8b1530-2f7a-4e07-a4a0-c8a690a97c25\\app-debug.apk");
                        if(file.exists()) {
                            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\nContent-Length: ".getBytes());
                            out.write((file.length() + "\r\n\r\n").getBytes());
                            Files.copy(file.toPath(), out);
                        } else {
                            out.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
                        }
                        out.flush();
                    }
                    client.close();
                } catch(Exception e) {}
            }).start();
        }
    }
}
