using System;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;

class Program {
    static void Main() {
        using (TcpClient client = new TcpClient("attendenceapp-axf9.onrender.com", 443))
        using (SslStream sslStream = new SslStream(client.GetStream(), false, 
            new RemoteCertificateValidationCallback((sender, certificate, chain, errors) => {
                foreach (var element in chain.ChainElements) {
                    X509Certificate2 cert = element.Certificate;
                    Console.WriteLine("Subject: " + cert.Subject);
                    Console.WriteLine("Issuer: " + cert.Issuer);
                    
                    // We need the SPKI hash. C# doesn't expose SPKI directly easily.
                    // But we can just use openssl.
                }
                return true;
            }))) 
        {
            sslStream.AuthenticateAsClient("attendenceapp-axf9.onrender.com");
        }
    }
}
