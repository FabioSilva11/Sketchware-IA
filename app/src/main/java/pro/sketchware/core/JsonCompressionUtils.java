package pro.sketchware.core;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public class JsonCompressionUtils {
   public static HashMap a(String param0) {
      HashMap var1 = new HashMap();
      if (!new File(param0).exists()) {
         return var1;
      }

      FileInputStream var5 = null;
      InputStreamReader var2 = null;
      try {
         XmlPullParser var4 = XmlPullParserFactory.newInstance().newPullParser();
         var5 = new FileInputStream(param0);
         var2 = new InputStreamReader(var5, "UTF-8");
         var4.setInput(var2);
         int var6 = var4.getEventType();
         while (var6 != 1) {
            HashMap var3 = var1;
            if (var6 == 2) {
               var3 = var1;
            } else if (var6 != 8) {
               var3 = var1;
            } else if ((var4.getLineNumber() == 1 || var4.getLineNumber() == 2) && var1.isEmpty()) {
               var3 = b(var4.getText());
            }

            var1 = var3;
            var6 = var4.nextToken();
         }

         var1.put("valid", Boolean.valueOf(true));
      } catch (Exception var7) {
         var1.put("valid", Boolean.valueOf(false));
      } finally {
         if (var5 != null) {
            try {
               var5.close();
            } catch (Exception ignored) {
            }
         }

         if (var2 != null) {
            try {
               var2.close();
            } catch (Exception ignored) {
            }
         }
      }

      return var1;
   }

   public static void a(String var0, String var1, String var2) throws Exception {
      Document var3 = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(var0));
      Element var4 = var3.getDocumentElement();
      NodeList var5 = var4.getParentNode().getChildNodes();
      StringBuilder var6 = new StringBuilder();
      var6.append("version=\"");
      var6.append(var1);
      var6.append("\" locale=\"");
      var6.append(var2);
      var6.append("\"");
      ProcessingInstruction var7 = var3.createProcessingInstruction("sketchware", var6.toString());
      if (var5.item(0).getNodeName().equals("sketchware")) {
         var4.getParentNode().removeChild(var5.item(0));
      }

      var4.getParentNode().insertBefore(var7, var4);
      DOMSource var8 = new DOMSource(var3);
      TransformerFactory.newInstance().newTransformer().transform(var8, new StreamResult(new File(var0)));
   }

   public static HashMap b(String var0) {
      StringBuilder var1 = new StringBuilder();
      var1.append("<");
      var1.append(var0);
      var1.append("/>");
      String var2 = var1.toString();
      HashMap var8 = new HashMap();

      NodeList var11;
      try {
         DocumentBuilder var9 = DocumentBuilderFactory.newInstance().newDocumentBuilder();
         ByteArrayInputStream var3 = new ByteArrayInputStream(var2.getBytes());
         var11 = var9.parse(var3).getElementsByTagName("sketchware");
         if (var11.getLength() <= 0) {
            return var8;
         }
      } catch (Exception var7) {
         boolean var10001 = false;
         return var8;
      }

      org.w3c.dom.Node var10;
      org.w3c.dom.Node var12;
      try {
         var10 = var11.item(0).getAttributes().getNamedItem("version");
         var12 = var11.item(0).getAttributes().getNamedItem("locale");
      } catch (Exception var6) {
         boolean var13 = false;
         return var8;
      }

      if (var10 != null) {
         try {
            var8.put("version", var10.getNodeValue());
         } catch (Exception var5) {
            boolean var14 = false;
            return var8;
         }
      }

      if (var12 != null) {
         try {
            var8.put("locale", var12.getNodeValue());
         } catch (Exception var4) {
            boolean var15 = false;
         }
      }

      return var8;
   }

   public byte[] a(byte[] var1) throws Exception {
      Cipher var2 = Cipher.getInstance("AES/CBC/PKCS5Padding");
      byte[] var3 = "sketchwarelangsc".getBytes();
      var2.init(2, new SecretKeySpec(var3, "AES"), new IvParameterSpec(var3));
      return var2.doFinal(var1);
   }

   public byte[] b(byte[] var1) throws Exception {
      Cipher var2 = Cipher.getInstance("AES/CBC/PKCS5Padding");
      byte[] var3 = "sketchwarelangsc".getBytes();
      var2.init(1, new SecretKeySpec(var3, "AES"), new IvParameterSpec(var3));
      return var2.doFinal(var1);
   }
}
