import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.Properties;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

public class GUI extends JFrame implements ActionListener, ChangeListener {

    private static final String PROPERTIES_PATH_NAME = "config.winBTProxmityClient";

    private JPanel root;

    private JButton btRun;
    private JButton btLoadCert;
    private JSlider slTime;

    private JTextField tfPort;
    private JTextField tfIP;
    private JTextField tfHMAC;

    private JLabel lIP;
    private JLabel lPort;
    private JLabel lCertPath;
    private JLabel lTime;
    private JLabel lSecLevel;

    private JCheckBox cbSilent;
    private JComboBox cbSecLevel;

    private BTClient mBTClient;
    private Preferences mPrefs;

    private Properties mProperties;
    private File mPropFile;
    private OutputStream mOutputProp;
    private FileInputStream mInputProp;

    private String mIP;
    private String mPort;
    private boolean mStartSilent;
    private String mCertPath;
    private int mTimeInterval;
    private int mLevel;
    private String mHmac;

    // constructor
    public GUI(BTClient _BTClient) {

        setResizable(false);
        setContentPane(root);
        pack();
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        mBTClient = _BTClient;
        initGUI();

        // try to load saved data
        if (loadPrefs()) {
            System.out.println("Could load prefs");
            setupValues(getIP(), getPort(), getCertPath(), getTimeInterval(), getLevel(), getHMAC());
        } else {
            System.out.println("Could NOT load prefs");
            setupValues("localhost", "4567", "MISSING!", 15, 1, "");  // setup default values
        }

        // start service (silently)
        if (isStartSilent()) {
            System.out.println("-> start silently");
            setVisible(false);
            initClient();
        } else {
            System.out.println("-> start normally");
            setVisible(true);
        }
    }

    // init and setup
    private void initGUI() {
        btRun.addActionListener(this);
        btLoadCert.addActionListener(this);

        slTime.setMinimum(1);
        slTime.setMaximum(240);
        slTime.addChangeListener(this);

        cbSecLevel.addItem("(1) Proximity Detection");
        cbSecLevel.addItem("(2) Proximity D. + OTP");
        cbSecLevel.addItem("(3) Proximity D. + OTP + HMAC");
        cbSecLevel.addActionListener(this);

        mProperties = new Properties();
        mPropFile = new File(PROPERTIES_PATH_NAME);
    }

    private void setupValues(String ip, String port, String pathCert, int timeInterval, int level, String hmac) {
        tfIP.setText(ip);
        tfPort.setText(port);

        if (pathCert.equals("MISSING!")) {
            lCertPath.setText(pathCert);
            lCertPath.setForeground(Color.RED);
        } else {
            String[] certpath = pathCert.split("\\\\");
            lCertPath.setText(certpath[certpath.length - 1]);
            lCertPath.setForeground(Color.GREEN);
        }

        setSliderValue(timeInterval);

        cbSecLevel.setSelectedIndex(level - 1);

        if (level == 3) {
            tfHMAC.setEnabled(true);
            tfHMAC.setText(hmac);
            validateHMAC();
        } else {
            tfHMAC.setText("");
            tfHMAC.setEnabled(false);
        }
    }

    private void setSliderValue(int timeInterval) {
        slTime.setValue(timeInterval / 15);

        StringBuilder time = new StringBuilder();
        int sec;
        int min;
        int h;

        h = timeInterval / 3600;
        min = (timeInterval % 3600) / 60;
        sec = timeInterval % 60;

        if (sec >= 1) {
            time.insert(0, String.valueOf(sec) + "s");
        }

        if (min >= 1) {
            time.insert(0, String.valueOf(min) + "m ");
        }

        if (h >= 1) {
            time.insert(0, String.valueOf(h) + "h");
        }

        lTime.setText(time.toString());
        setTimeInterval(timeInterval);
    }

    private boolean validateIP() {
        String ip = tfIP.getText();
        if (ip.isEmpty()) {
            return false;
        } else if (ip.equals("localhost")) {
            setIP(ip);
            return true;
        } else {
            Pattern ipPattern = Pattern.
                    compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
            if (ipPattern.matcher(ip).matches()) {
                setIP(ip);
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean validatePort() {
        String port = tfPort.getText();
        if (port.isEmpty()) {
            return false;
        } else {
            Pattern portPattern = Pattern.
                    compile("^[0-9]+$");
            if (portPattern.matcher(port).matches()) {
                setPort(port);
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean validateHMAC() {
        String hmac = tfHMAC.getText();
        if(hmac.isEmpty() && getLevel() == 3) {
            return false;
        } else {
            setHMAC(hmac);
            return true;
        }
    }

    private void initClient() {
        mBTClient.setLevel(getLevel());
        validateHMAC();
        mBTClient.setHMAC(getHMAC());
        mBTClient.setHostURL(getIP() + ":" + getPort());
        mBTClient.setTimeInterval(getTimeInterval());
        mBTClient.setCertPath(getCertPath());

        mBTClient.setActivated(true);
    }

    // data persistence
    private boolean loadPrefFile() {
        if (!mPropFile.exists()) {
            try {
                return mPropFile.createNewFile();
            } catch (IOException e) {
                System.out.println("can't create prop file");
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    private boolean loadPrefs() {
        String errStringLoad = "-1";
        int errIntLoad = Integer.MAX_VALUE;

        try {
            if (!loadPrefFile()) {
                return false;
            }

            mInputProp = new FileInputStream(mPropFile);
            mProperties.load(mInputProp);

            setIP(mProperties.getProperty("ip", errStringLoad));
            setPort(mProperties.getProperty("port", errStringLoad));
            setTimeInterval(Integer.parseInt(mProperties.getProperty("timeInterval", String.valueOf(errIntLoad))));
            setCertPath(mProperties.getProperty("pemLocation", errStringLoad));
            setLevel(Integer.parseInt(mProperties.getProperty("level", String.valueOf(errIntLoad))));

            setStartSilent(Boolean.parseBoolean(mProperties.getProperty("silentStart", String.valueOf(false))));
            setHMAC(mProperties.getProperty("hmac", errStringLoad));

        } catch (IOException e) {
            System.out.println("can'read prop file");
            e.printStackTrace();
            return false;
        } finally {
            if (mInputProp != null) {
                try {
                    mInputProp.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return !getIP().equals(errStringLoad)
                && !getPort().equals(errStringLoad)
                && getTimeInterval() != errIntLoad
                && !getCertPath().equals(errStringLoad)
                && getLevel() != errIntLoad;
    }

    public void savePrefs() {
        savePrefs(getIP(), getPort(), getTimeInterval(), getCertPath(), cbSilent.isSelected(), getLevel(), getHMAC());
    }

    private void savePrefs(String _ip, String _port, int _timeInterval, String _pemLocation, boolean _silent, int _level, String _hmac) {
        try {
            if (!loadPrefFile()) {
                return;
            }

            mOutputProp = new FileOutputStream(PROPERTIES_PATH_NAME);

            // save properties
            mProperties.setProperty("ip", _ip);
            mProperties.setProperty("port", _port); // mind default
            mProperties.setProperty("timeInterval", String.valueOf(_timeInterval));
            mProperties.setProperty("pemLocation", _pemLocation);
            mProperties.setProperty("silentStart", String.valueOf(_silent));
            mProperties.setProperty("level", String.valueOf(_level));
            mProperties.setProperty("hmac", _hmac);

            mProperties.store(mOutputProp, null);
        } catch (IOException e) {
            System.out.println("Can't save properties");
            e.printStackTrace();
        }
    }

    // Listeners for buttons and slider
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource().equals(btRun)) {
            if (validateIP() && validatePort() && mBTClient.loadDaemonCert()) {
                savePrefs(getIP(), getPort(), getTimeInterval(), getCertPath(), cbSilent.isSelected(), getLevel(), getHMAC());
                initClient();
                mBTClient.start();
            }
            // error message?
        } else if (e.getSource().equals(btLoadCert)) {
            JFileChooser jFileChooser = new JFileChooser();
            FileNameExtensionFilter extFilter = new FileNameExtensionFilter("PEM Certificate", "pem", "PEM");
            jFileChooser.setFileFilter(extFilter);

            int returnVal = jFileChooser.showOpenDialog(btLoadCert);

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File selectedFile = jFileChooser.getSelectedFile();
                mBTClient.setCertPath(selectedFile.getAbsolutePath());
                if (mBTClient.loadDaemonCert()) {
                    setCertPath(selectedFile.getAbsolutePath());
                    lCertPath.setText(selectedFile.getName());
                    lCertPath.setForeground(Color.GREEN);
                } else {
                    // show Dialog
                    lCertPath.setText("INVALID Certificate");
                    lCertPath.setForeground(Color.RED);
                }
            }
        } else if (e.getSource().equals(cbSecLevel)) {
            setLevel(cbSecLevel.getSelectedIndex() + 1);
            if (getLevel() == 3) {
                tfHMAC.setEnabled(true);
            } else {
                tfHMAC.setEnabled(false);
            }
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        setSliderValue(slTime.getValue() * 15);
    }

    // Getter && Setter
    private boolean isStartSilent() {
        return mStartSilent;
    }

    private void setStartSilent(boolean _silentStart) {
        mStartSilent = _silentStart;
    }

    private String getPort() {
        return mPort;
    }

    private void setPort(String _port) {
        mPort = _port;
    }

    private String getIP() {
        return mIP;
    }

    private void setIP(String _ip) {
        mIP = _ip;
    }

    private int getTimeInterval() {
        return mTimeInterval;
    }

    private void setTimeInterval(int _time) {
        mTimeInterval = _time;
    }

    private String getCertPath() {
        return mCertPath;
    }

    private void setCertPath(String _certPath) {
        mCertPath = _certPath;
    }

    private String getHMAC() {
        return mHmac;
    }

    private void setHMAC(String _hmac) {
        mHmac = _hmac;
    }

    private int getLevel() {
        return mLevel;
    }

    private void setLevel(int _level) {
        mLevel = _level;
    }

}
