package com.moonligh.pay.infrastructure.channel;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moonligh.pay.PayProperties;
import com.moonligh.pay.domain.PayCommand;
import com.moonligh.pay.domain.PayException;

/**
 * 银联前台网关 5.1.0。官网 Java SDK 不发布到 Maven Central，签名、自动提交表单和验签
 * 与 AcpService.sign、createAutoFormHtml、validate 相同。
 */
public final class BankCashier {

    private static final Logger log = LoggerFactory.getLogger(BankCashier.class);
    private static final DateTimeFormatter TXN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final PayProperties properties;

    public BankCashier(PayProperties properties) {
        this.properties = properties;
    }

    public String prepay(PayCommand command) {
        PayProperties.Bank bank = properties.getBank();
        if (!bank.ready() || blank(properties.getNotifyBaseUrl())) {
            throw new PayException("PAY_CHANNEL_NOT_READY");
        }
        try {
            Map<String, String> data = new TreeMap<>();
            data.put("version", "5.1.0");
            data.put("encoding", "UTF-8");
            data.put("signMethod", "01");
            data.put("txnType", "01");
            data.put("txnSubType", "01");
            data.put("bizType", "000201");
            data.put("channelType", "07");
            data.put("accessType", "0");
            data.put("merId", bank.getMerId().trim());
            data.put("orderId", command.orderId());
            data.put("txnTime", ZonedDateTime.now(SHANGHAI).format(TXN_TIME));
            data.put("currencyCode", "156");
            data.put("txnAmt", Integer.toString(fen(command)));
            data.put("frontUrl", blankToEmpty(properties.getReturnUrl()));
            data.put("backUrl", properties.notifyUrl("bank"));
            data.put("signature", sign(cover(data), privateKey(bank)));
            return form(bank.getFrontUrl().trim(), data);
        } catch (PayException error) {
            throw error;
        } catch (Exception error) {
            log.warn("银联下单失败 type={}", error.getClass().getSimpleName());
            throw new PayException("PAYMENT_PENDING");
        }
    }

    public boolean verify(Map<String, String> notice) {
        PayProperties.Bank bank = properties.getBank();
        if (blank(bank.getUnionpayCertPath()) || notice == null) {
            return false;
        }
        String signature = notice.get("signature");
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey(bank.getUnionpayCertPath().trim()));
            verifier.update(cover(notice).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception error) {
            log.warn("银联回调验签失败 type={}", error.getClass().getSimpleName());
            return false;
        }
    }

    static int fen(PayCommand command) {
        return command.amountYuan().movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    public static String cover(Map<String, String> data) {
        return data.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .filter(entry -> !"signature".equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private static String sign(String content, PrivateKey key) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key);
        signer.update(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private static PrivateKey privateKey(PayProperties.Bank bank) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(Path.of(bank.getSignCertPath().trim()))) {
            store.load(input, bank.getSignCertPassword().toCharArray());
        }
        String alias = store.aliases().nextElement();
        return (PrivateKey) store.getKey(alias, bank.getSignCertPassword().toCharArray());
    }

    private static PublicKey publicKey(String path) throws Exception {
        try (var input = Files.newInputStream(Path.of(path))) {
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(input);
            return certificate.getPublicKey();
        }
    }

    private static String form(String action, Map<String, String> data) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body onload=\"document.forms[0].submit()\">");
        html.append("<form method=\"post\" action=\"").append(escape(action)).append("\">");
        for (Map.Entry<String, String> entry : data.entrySet()) {
            html.append("<input type=\"hidden\" name=\"")
                    .append(escape(entry.getKey()))
                    .append("\" value=\"")
                    .append(escape(entry.getValue()))
                    .append("\"/>");
        }
        html.append("</form></body></html>");
        return html.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
