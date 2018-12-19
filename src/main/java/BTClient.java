import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

public class BTClient {

    private boolean activated;
    private int mTimeInterval;
    private String hostURL;
    private String mPath;
    private int level;
    private String hmac;

    private HttpsURLConnection mConnection;

    static {
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return hostname.equals("192.168.2.2");
            }
        });
    }

    private X509Certificate mBTx509Cert;
    private PublicKey mBTPublicKey;

    private GUI mClientGUI;
    private SystemTrayMenu mSystemTrayMenu;

    public static void main(String[] args) {
        BTClient client = new BTClient();

        // start automatically if silent start trigged it
        if (client.isActivated()) {
            client.start();
        }
    }

    private BTClient() {
        setGUI(new GUI(this));
        setSystemTrayMenu(new SystemTrayMenu(this, getGUI()));
    }

    public void start() {
        getSystemTrayMenu().updateEnabled(isActivated());
        while (isActivated()) {
            try {
                initConnection();
                connectAndVerifyConnection();
                queryBTResult();

                Thread.sleep((long) getTimeInterval() * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("\t\t[ERROR!] InterruptedException start(): " + isActivated());
                setActivated(false);
            }
        }
    }

    public boolean loadDaemonCert() {
        FileInputStream fileIn = null;
        boolean result;

        try {
            System.out.println("Cert path: " + getCertPath());
            if (getCertPath() == null) {
                return false;
            }
            File filePBKey = new File(getCertPath());
            fileIn = new FileInputStream(filePBKey);

            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            mBTx509Cert = (X509Certificate) certFactory.generateCertificate(fileIn);
            mBTPublicKey = mBTx509Cert.getPublicKey();

            result = true;
        } catch (IOException e) {
            System.out.println("[ERROR] IOException loadDaemonCert()\n" + e.getMessage());
            result = false;
        } catch (CertificateException e) {
            System.out.println("[ERROR] CertificateException loadDaemonCert()\n" + e.getMessage());
            // Parsing error while reading key

            result = false;
        } finally {
            if (fileIn != null) {
                try {
                    fileIn.close();
                } catch (IOException e) {
                    System.out.println("[ERROR] IOException (finally of) loadDaemonCert()\n" + e.getMessage());
                }
            }
        }

        return result;
    }

    private void initConnection() {
        try {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(null, null);
            keystore.setCertificateEntry("BT Daemon", mBTx509Cert);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keystore);

            SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(null, tmf.getTrustManagers(), null);

            SSLSocketFactory sslFactory = sslCtx.getSocketFactory();

            System.out.println("url: " + getHostURL());
            URL url = new URL(getHostURL());

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setSSLSocketFactory(sslFactory);

            setConnection(connection);
        } catch (IOException e) {
            System.out.println("\n[ERROR] IOException initConnection()\n" + e.getMessage() + "\n[END OF ERROR MESSAGE]");
            e.printStackTrace();
        } catch (KeyStoreException e) {
            System.out.println("[ERROR] KeyStoreException initConnection()\n" + e.getMessage());
        } catch (CertificateException e) {
            System.out.println("[ERROR] CertificateException initConnection()\n" + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            System.out.println("[ERROR] NoSuchAlgorithmException initConnection()\n" + e.getMessage());
        } catch (KeyManagementException e) {
            System.out.println("[ERROR] KeyManagementException initConnection()\n" + e.getMessage());
        }
    }

    private HttpsURLConnection getConnection() {
        return mConnection;
    }

    private void setConnection(HttpsURLConnection _conn) {
        mConnection = _conn;
    }

    private void connectAndVerifyConnection() {
        if (getConnection() != null) {
            try {
                getConnection().connect();

                Certificate[] listCerts = getConnection().getServerCertificates();
                for (Certificate cert : listCerts) {
                    cert.verify(mBTPublicKey);
                }
            } catch (IOException e) {
                System.out.println("[ERROR] IOE connectAndVerifyConnection()\n" + e.getMessage());

                setActivated(false);
                disconnectConnection();

            } catch (CertificateException e) {
                System.out.println("[ERROR] CertificateException connectAndVerifyConnection()\n" + e.getMessage());
                setActivated(false);
            } catch (NoSuchAlgorithmException e) {
                System.out.println("[ERROR] NoSuchAlgorithmException connectAndVerifyConnection()\n" + e.getMessage());
                setActivated(false);
            } catch (InvalidKeyException e) {
                System.out.println("[ERROR] Invalid Key!\n" + "Connection will be terminated");
                lockscreen();

                disconnectConnection();
                setActivated(false);
            } catch (SignatureException e) {
                System.out.println("[ERROR] SignatureException connectAndVerifyConnection()\n" + e.getMessage());
                setActivated(false);
            } catch (NoSuchProviderException e) {
                System.out.println("[ERROR] NoSuchProviderException connectAndVerifyConnection()\n" + e.getMessage());
                setActivated(false);
            }
        }
    }

    public void disconnectConnection() {
        if (getConnection() != null) {
            mConnection.disconnect();
        }
    }

    private void queryBTResult() {
        if (getConnection() == null) {
            return;
        }

        Map<String, List<String>> headers = getConnection().getHeaderFields();

        List<String> ListFoundBT = headers.get("foundBT");
        if (ListFoundBT != null) {
            for (String foundBTResult : ListFoundBT) {
                System.out.print("foundBT: " + foundBTResult);
                if (!foundBTResult.equals("true")) {
                    lockscreen();
                }
            }
        }

        disconnectConnection();
    }

    private void lockscreen() {
        try {
            System.out.println("Locked Screen!");
            // Execute Windows Screen Look
            final String path = System.getenv("windir") + File.separator + "System32" + File.separator + "rundll32.exe";
            Runtime runtime = Runtime.getRuntime();
            Process pr = runtime.exec(path + " user32.dll,LockWorkStation");
            pr.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

    }

    // Getters & Setters
    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean _activated) {
        System.out.println("set -> " + _activated);
        activated = _activated;
    }

    private int getTimeInterval() {
        return mTimeInterval;
    }

    public void setTimeInterval(int _time) {
        mTimeInterval = _time;
    }

    private String getHostURL() {
        return hostURL;
    }

    public void setHostURL(String _url) {
        hostURL = "https://" + _url + "/checkForAuthToken?level=" + String.valueOf(getLevel());
        if (getLevel() == 3 && !getHMAC().equals("-1")) {
            hostURL = "https://" + _url + "/checkForAuthToken?level=" + String.valueOf(getLevel() + "&hmac=" + getHMAC());
        }
    }

    private String getCertPath() {
        return mPath;
    }

    public void setCertPath(String _path) {
        mPath = _path;
    }

    private GUI getGUI() {
        return mClientGUI;
    }

    private void setGUI(GUI _gui) {
        mClientGUI = _gui;
    }

    private SystemTrayMenu getSystemTrayMenu() {
        return mSystemTrayMenu;
    }

    private void setSystemTrayMenu(SystemTrayMenu _trayMenu) {
        mSystemTrayMenu = _trayMenu;
    }

    private int getLevel() {
        return level;
    }

    public void setLevel(int _level) {
        level = _level;
    }

    private String getHMAC() {
        return hmac;
    }

    public void setHMAC(String _hmac) {
        hmac = _hmac;
    }
}
