package com.moonligh.pay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 商户参数。密钥和证书路径只写在 consult-payment/config/application-local.yml。
 */
@ConfigurationProperties(prefix = "pay")
public class PayProperties {

    private String token = "";
    private String notifyBaseUrl = "";
    private String returnUrl = "";
    private final Alipay alipay = new Alipay();
    private final Wechat wechat = new Wechat();
    private final Bank bank = new Bank();

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNotifyBaseUrl() {
        return notifyBaseUrl;
    }

    public void setNotifyBaseUrl(String notifyBaseUrl) {
        this.notifyBaseUrl = notifyBaseUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public Alipay getAlipay() {
        return alipay;
    }

    public Wechat getWechat() {
        return wechat;
    }

    public Bank getBank() {
        return bank;
    }

    public String notifyUrl(String channel) {
        String base = notifyBaseUrl == null ? "" : notifyBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/pay/notify/" + channel;
    }

    public boolean channelReady(String channel) {
        return switch (Channels.normalize(channel)) {
            case "alipay" -> alipay.ready();
            case "wechat" -> wechat.ready();
            case "bank" -> bank.ready();
            default -> false;
        };
    }

    public static class Alipay {
        private String appId = "";
        private String privateKey = "";
        private String alipayPublicKey = "";
        private String serverUrl = "https://openapi.alipay.com/gateway.do";

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }

        public String getAlipayPublicKey() {
            return alipayPublicKey;
        }

        public void setAlipayPublicKey(String alipayPublicKey) {
            this.alipayPublicKey = alipayPublicKey;
        }

        public String getServerUrl() {
            return serverUrl;
        }

        public void setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        public boolean ready() {
            return filled(appId) && filled(privateKey) && filled(alipayPublicKey) && filled(serverUrl);
        }
    }

    public static class Wechat {
        private String appId = "";
        private String mchId = "";
        private String apiV3Key = "";
        private String certSerialNo = "";
        private String privateKeyPath = "";
        private String publicKeyPath = "";
        private String publicKeyId = "";

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getMchId() {
            return mchId;
        }

        public void setMchId(String mchId) {
            this.mchId = mchId;
        }

        public String getApiV3Key() {
            return apiV3Key;
        }

        public void setApiV3Key(String apiV3Key) {
            this.apiV3Key = apiV3Key;
        }

        public String getCertSerialNo() {
            return certSerialNo;
        }

        public void setCertSerialNo(String certSerialNo) {
            this.certSerialNo = certSerialNo;
        }

        public String getPrivateKeyPath() {
            return privateKeyPath;
        }

        public void setPrivateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
        }

        public String getPublicKeyPath() {
            return publicKeyPath;
        }

        public void setPublicKeyPath(String publicKeyPath) {
            this.publicKeyPath = publicKeyPath;
        }

        public String getPublicKeyId() {
            return publicKeyId;
        }

        public void setPublicKeyId(String publicKeyId) {
            this.publicKeyId = publicKeyId;
        }

        public boolean ready() {
            return filled(appId)
                    && filled(mchId)
                    && filled(apiV3Key)
                    && filled(certSerialNo)
                    && filled(privateKeyPath)
                    && filled(publicKeyPath)
                    && filled(publicKeyId);
        }
    }

    public static class Bank {
        private String merId = "";
        private String signCertPath = "";
        private String signCertPassword = "";
        private String unionpayCertPath = "";
        private String frontUrl = "https://gateway.95516.com/gateway/api/frontTransReq.do";

        public String getMerId() {
            return merId;
        }

        public void setMerId(String merId) {
            this.merId = merId;
        }

        public String getSignCertPath() {
            return signCertPath;
        }

        public void setSignCertPath(String signCertPath) {
            this.signCertPath = signCertPath;
        }

        public String getSignCertPassword() {
            return signCertPassword;
        }

        public void setSignCertPassword(String signCertPassword) {
            this.signCertPassword = signCertPassword;
        }

        public String getUnionpayCertPath() {
            return unionpayCertPath;
        }

        public void setUnionpayCertPath(String unionpayCertPath) {
            this.unionpayCertPath = unionpayCertPath;
        }

        public String getFrontUrl() {
            return frontUrl;
        }

        public void setFrontUrl(String frontUrl) {
            this.frontUrl = frontUrl;
        }

        public boolean ready() {
            return filled(merId) && filled(signCertPath) && filled(signCertPassword) && filled(frontUrl);
        }
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }
}
