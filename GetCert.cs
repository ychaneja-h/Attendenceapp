using System;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

class Program {
    static void Main() {
        using (TcpClient client = new TcpClient("attendenceapp-axf9.onrender.com", 443))
        using (SslStream sslStream = new SslStream(client.GetStream(), false, 
            new RemoteCertificateValidationCallback((sender, certificate, chain, errors) => true))) {
            
            sslStream.AuthenticateAsClient("attendenceapp-axf9.onrender.com");
            X509Certificate2 cert = new X509Certificate2(sslStream.RemoteCertificate);
            
            string hash = Convert.ToBase64String(cert.GetPublicKey());
            Console.WriteLine("Public Key Base64: " + hash);

            // Calculate SPKI Hash (SHA-256 of the SubjectPublicKeyInfo)
            // C# X509Certificate2 doesn't have an easy way to get the exact SPKI bytes, 
            // but we can just use openssl.
        }
    }
}
