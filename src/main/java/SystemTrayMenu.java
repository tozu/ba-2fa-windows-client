import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.PaintEvent;

/**
 * Created by Tobias on 7/10/2016.
 */
public class SystemTrayMenu implements ActionListener {

    private MenuItem close, action, show;
    private final BTClient mBTClient;
    private final GUI mGUI;

    public SystemTrayMenu(BTClient _btClient, GUI _gui) {
        mBTClient = _btClient;
        mGUI = _gui;

        initTrayMenu();
    }

    private void initTrayMenu() {
        if (!SystemTray.isSupported()) {
            System.out.println("System tray is not supported");
            System.exit(0);
            return;
        }
        SystemTray systemTray = SystemTray.getSystemTray();
        Image image = Toolkit.getDefaultToolkit().getImage("src/images/icon.png");

        PopupMenu trayPopupMenu = new PopupMenu();

        show = new MenuItem("Show BT Client");
        show.addActionListener(this);
        trayPopupMenu.add(show);

        action = new MenuItem("activated?");
        action.addActionListener(this);
        trayPopupMenu.add(action);

        close = new MenuItem("Close");
        close.addActionListener(this);
        trayPopupMenu.add(close);

        //setting tray icon
        TrayIcon trayIcon = new TrayIcon(image, "2nd Factor BT Proximity Client", trayPopupMenu);
        trayIcon.setImageAutoSize(true);

        try {
            systemTray.add(trayIcon);
        } catch (AWTException awtException) {
            awtException.printStackTrace();
        }
    }

    public void actionPerformed(ActionEvent e) {
        switch (e.getActionCommand()) {
            case "Show BT Client":
                getGUI().setVisible(true);
                break;
            case "activated":
            case "de-activated":
                if (getBTClient().isActivated()) {
                    getBTClient().setActivated(false);
                } else {
                    getBTClient().setActivated(true);
                }
                break;
            case "Close":
                getBTClient().disconnectConnection();
                getGUI().savePrefs();
                System.exit(0);
                break;
        }
    }

    private GUI getGUI() {
        return mGUI;
    }

    private BTClient getBTClient() {
        return mBTClient;
    }

    public void updateEnabled(boolean _activated) {
        if (_activated) {
            action.setLabel("activated");
        } else {
            action.setLabel("de-activated");
        }
    }
}
