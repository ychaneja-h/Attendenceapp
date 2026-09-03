using System;
using System.Net;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

public class PinFetcher {
    public static void Main() {
        ServicePointManager.ServerCertificateValidationCallback = (sender, cert, chain, errors) => {
            var hash = new SHA256Managed().ComputeHash(cert.GetPublicKey());
            Console.WriteLine("Leaf: " + Convert.ToBase64String(hash));
            foreach (var element in chain.ChainElements) {
                var cHash = new SHA256Managed().ComputeHash(element.Certificate.GetPublicKey());
                Console.WriteLine("Chain: " + Convert.ToBase64String(cHash));
            }
            return true;
        };
        var req = WebRequest.Create("https://attendenceapp-axf9.onrender.com");
        req.GetResponse().Close();
    }
}
