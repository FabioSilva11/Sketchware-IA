package pro.sketchware.compiler;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Date;
import kellinwood.security.zipsigner.ZipSigner;
import kellinwood.security.zipsigner.optional.JksKeyStore;
import kellinwood.security.zipsigner.optional.LoadKeystoreException;
import org.spongycastle.jce.X509Principal;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.x509.X509V3CertificateGenerator;
import android.util.Log;

import pro.sketchware.filemanagers.FileCryptoUtils;
import pro.sketchware.workspace.WorkspacePathManager;

public class ApkSigningManager {
   public KeyStore a;
   public ByteBuffer b;

   public ApkSigningManager() {
      Security.addProvider(new BouncyCastleProvider());
      this.a = new JksKeyStore();
   }

   public String a() throws Exception {
      Enumeration var1 = this.a.aliases();
      return var1.hasMoreElements() ? (String)var1.nextElement() : "";
   }

   public void a(InputStream var1, String var2) throws Exception {
      if (var1 != null) {
         try {
            this.a.load(var1, var2.toCharArray());
         } catch (Exception var9) {
            var9.printStackTrace();
            Exception var3 = new Exception(var9.getMessage());
            throw var3;
         } finally {
            try {
               var1.close();
            } catch (Exception var8) {
            }

         }

      }
   }

   public void a(String var1, String var2) throws Exception {
      this.a((InputStream)(new FileInputStream(new File(var1))), var2);
   }

   public void a(String var1, String var2, int var3, String var4, String var5) throws Exception {
      byte[] var6 = this.a(var2, var3, var4, var5);
      File var7 = new File(WorkspacePathManager.i());
      if (!var7.exists()) {
         var7.mkdirs();
      }

      (new FileCryptoUtils()).a(var1, var6);
   }

   public final byte[] a(String var1) throws Exception {
      if (this.a == null) {
         return null;
      } else {
         this.b = ByteBuffer.allocate(8192);
         ApkSigningOutputStream var2 = new ApkSigningOutputStream(this);
         this.a.store(var2, var1.toCharArray());
         byte[] var8 = new byte[this.b.position()];
         System.arraycopy(this.b.array(), 0, var8, 0, this.b.position());
         int var3 = var8.length;
         var1 = "";

         for(int var4 = 0; var4 < var3; ++var4) {
            byte var5 = var8[var4];
            StringBuilder var6 = new StringBuilder();
            var6.append(var1);
            var6.append(String.format("%02X", var5));
            var1 = var6.toString();
         }

         return var8;
      }
   }

   public byte[] a(String param1, int param2, String param3, String param4) throws Exception {
      JksKeyStore keystore;
      X509V3CertificateGenerator generator;
      KeyPair keyPair;
      X509Principal subject;
      long serialNumber;

      try {
         KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
         keyPairGen.initialize(1024, SecureRandom.getInstance("SHA1PRNG"));
         keyPair = keyPairGen.generateKeyPair();
         generator = new X509V3CertificateGenerator();
         subject = new X509Principal(param1);
         int serial = new SecureRandom().nextInt();
         serialNumber = serial < 0 ? (long)(serial * -1) : serial;

         generator.setSerialNumber(BigInteger.valueOf(serialNumber));
         generator.setIssuerDN(subject);
         Date notBefore = new Date(System.currentTimeMillis());
         generator.setNotBefore(notBefore);
         Date notAfter = new Date(System.currentTimeMillis() + param2 * 31536000000L);
         generator.setNotAfter(notAfter);
         generator.setSubjectDN(subject);
         generator.setPublicKey(keyPair.getPublic());
         generator.setSignatureAlgorithm("MD5WithRSAEncryption");
         X509Certificate cert = generator.generateX509Certificate(keyPair.getPrivate());
         keystore = new JksKeyStore();
         this.a = keystore;
         this.a.load(null, param4.toCharArray());
         this.a.setKeyEntry(param3, keyPair.getPrivate(), param4.toCharArray(), new Certificate[]{cert});
      } catch (LoadKeystoreException ex) {
         Log.e("ERROR", "Failed to access keystore. incorrect passward");
         throw ex;
      } catch (Exception ex) {
         throw ex;
      }

      return this.a(param4);
   }

   public ZipSigner b(String var1) throws Exception {
      ZipSigner var2 = new ZipSigner();
      var2.issueLoadingCertAndKeysProgressEvent();
      String var3 = this.a();
      var2.setKeys("custom", (X509Certificate)this.a.getCertificate(var3), (PrivateKey)this.a.getKey(var3, var1.toCharArray()), "SHA1WITHRSA", (byte[])null);
      return var2;
   }
}




