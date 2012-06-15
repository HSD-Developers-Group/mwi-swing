package eu.kprod;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;

import org.apache.log4j.Logger;

import eu.kprod.ds.MwDataModel;
import eu.kprod.ds.MwDataSourceListener;
import eu.kprod.ds.MwSensorClassHUD;
import eu.kprod.ds.MwSensorClassIMU;
import eu.kprod.ds.MwSensorClassMotor;
import eu.kprod.ds.MwSensorClassServo;
import eu.kprod.gui.DebugFrame;
import eu.kprod.gui.LogViewerFrame;
import eu.kprod.gui.MwJButton;
import eu.kprod.gui.MwMainPanel;
import eu.kprod.gui.MwSensorCheckBoxJPanel;
import eu.kprod.gui.changepanel.MwBOXPanel;
import eu.kprod.gui.changepanel.MwPIDPanel;
import eu.kprod.gui.chart.MwChartFactory;
import eu.kprod.gui.chart.MwChartPanel;
import eu.kprod.gui.comboBox.MwJComboBox;
import eu.kprod.gui.hud.MwHudPanel;
import eu.kprod.serial.SerialCom;
import eu.kprod.serial.SerialDevice;
import eu.kprod.serial.SerialException;
import eu.kprod.serial.SerialListener;
import eu.kprod.serial.SerialNotFoundException;

/**
 * Known issues
 * 
 * - when zooming the chart : news values are still recorded so due to the
 * dataSource maxItemcounts and AgeLimite , the chart gets emptied at the zoomed
 * date
 * 
 * @author treym
 * 
 */
public class MwGuiFrame extends JFrame implements SerialListener {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(MwGuiFrame.class);
    private static MwGuiFrame instance;

    class actionMspSender implements ActionListener{
        
        private int[] requests;
        public actionMspSender( int[] requests1){
            this.requests = requests1;
        }
            public actionMspSender(int msp) {
               this.requests = new int[1];
                this.requests[0]=msp;
        }
            public void actionPerformed(ActionEvent e) {
                
                try {
                    beginSerialCom();
                    boolean restart = false;
                    if (timer!=null){
                    stopTimer();
                    restart=true;
                    }
                    for (int i : requests) {
                        send(MSP.request(i));
                        try {
                           Thread.sleep(14);
                       } catch (InterruptedException e1) {
                           e1.printStackTrace();
                       }
                   } 
                   if (restart) {
                       restartTimer(defaultRefreshRate);
                   }
               } catch (SerialException e1) {
                   e1.printStackTrace();
               } 
            }
    }

    /**
     * @param args
     * @throws SerialException
     */
    public static void main(String[] args) {


        SwingUtilities.invokeLater(new Runnable() {
            public void run() {

                if (System.getProperty("os.name").toLowerCase().indexOf("mac") >= 0) {
                    System.setProperty("apple.laf.useScreenMenuBar", "true");
                }
                // Turn off metal's use of bold fonts
                UIManager.put("swing.boldMetal", Boolean.FALSE);

                MwGuiFrame.getInstance().setVisible(true);

            }

        });

    }

    protected static MwGuiFrame getInstance() {
        // TODO Auto-generated method stub
        if (instance ==null){
            instance = new MwGuiFrame();
        }
        return instance;
    }

    public static final List<Integer> SerialRefreshRateStrings = initializeMap();
    private static final Integer DEFAULT_BAUDRATE = 115200;

    private static List<Integer> initializeMap() {
        List<Integer> m = new ArrayList<Integer>();
        m.add(1);
        m.add(2);
        m.add(5);
        m.add(10);
        m.add(15);
        m.add(20);
        m.add(25);
//        m.add(30);
//        m.add(40);
//        m.add(50);

        return Collections.unmodifiableList(m);
    }


    private static SerialCom com;

    private static Timer timer;

    private static DebugFrame debugFrame;
    private static LogViewerFrame motorFrame;
    private static LogViewerFrame servoFrame;
    
    private JPanel realTimePanel;

    private static JMenu serialMenuPort;
    private static ButtonGroup baudRateMenuGroup;
    private static ButtonGroup portNameMenuGroup;
    private JPanel settingsPanel;
    private static Integer defaultRefreshRate = 10;
    private static JMenuItem rescanSerial;
    private static JMenuItem disconnectSerial;
    private String frameTitle;
    private static MwHudPanel hudPanel;
    private static MwSensorCheckBoxJPanel realTimeCheckBoxPanel;

    private JPanel getRawImuChartPanel() {

        if (realTimePanel == null) {

            JButton stopButton = new MwJButton("Stop","Stop monitoring");
            stopButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    logger.trace("actionPerformed "
                            + e.getSource().getClass().getName());
                    stopTimer();
                }
            });

            
            final MwJComboBox serialRefreshRate = new MwJComboBox("Refresh rate (hz)",
                    (Integer[]) SerialRefreshRateStrings
                            .toArray(new Integer[SerialRefreshRateStrings.size()]) );
            serialRefreshRate.setMaximumSize(serialRefreshRate.getMinimumSize());
            serialRefreshRate.setSelectedIndex(3);
            serialRefreshRate.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent event) {
                    if (timer != null) {
                        restartTimer((Integer)serialRefreshRate.getSelectedItem());
                    }

                }
            });
            
            final MwChartPanel realTimeChart = MwChartFactory.createChart(MSP
                    .getRealTimeData().getDataSet(MwSensorClassIMU.class));
            MSP.getRealTimeData()
                    .addListener(MwSensorClassIMU.class,
                            (MwDataSourceListener) realTimeChart);

            realTimeChart.setPreferredSize(new java.awt.Dimension(700, 400));

            realTimePanel = new JPanel();
            realTimePanel.setLayout(new BorderLayout());
            realTimePanel.add(realTimeChart, BorderLayout.CENTER);

            JButton startButton = new MwJButton("Start","Start monitoring"); 
            startButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    logger.trace("actionPerformed "
                            + e.getSource().getClass().getName());

                    beginSerialCom();
                    restartTimer((Integer)serialRefreshRate.getSelectedItem());
                    realTimeChart.restoreAutoBounds();
                }
            });
            
            JPanel pane = new JPanel();
            pane.setLayout(new FlowLayout(FlowLayout.LEADING));
            pane.setBorder(new EmptyBorder(1, 1, 1, 1));
     
            pane.add(stopButton );
            pane.add(startButton);
            pane.add(serialRefreshRate);

            realTimePanel.add(pane, BorderLayout.SOUTH);
            realTimePanel.add(getHudPanel() ,BorderLayout.EAST);
            realTimePanel.add(getRealTimeCheckBowPanel() ,BorderLayout.WEST);
        }

        return realTimePanel;
    }

    private static MwSensorCheckBoxJPanel getRealTimeCheckBowPanel() {
        // TODO Auto-generated method stub
       if (realTimeCheckBoxPanel==null){
           realTimeCheckBoxPanel= new MwSensorCheckBoxJPanel();
       }
        return realTimeCheckBoxPanel;
    }

    protected static void beginSerialCom() {
        boolean openCom = false;
        try {
            if (!getCom().isOpen()) {
                openCom = true;
            }
        } catch (SerialException e1) {
            openCom = true;
        } finally {
            if (openCom) {
                try{
                openSerialPort();
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static MwHudPanel getHudPanel() {
        
        
        
        if(hudPanel==null){
            
            hudPanel= new MwHudPanel();
            MSP.getRealTimeData().addListener(MwSensorClassHUD.class, (MwDataSourceListener) hudPanel);
            
        }
        return hudPanel;
        
    }

    public MwGuiFrame() {
        super();
       
        
        MSP.setModel(new MwDataModel());

        
        {
            try {
                URL url = ClassLoader.getSystemResource("app.properties");
                final Properties appProps = new Properties();
                appProps.load(url.openStream());
                frameTitle = appProps.getProperty("mainframe.title");
            } catch (Exception e) {   
                throw new MwGuiRuntimeException("Failed to load app properties", e);
            }
        }
        
        this.setTitle(null);
        
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        this.setJMenuBar(createMenuBar());

        this.addWindowListener(new WindowAdapter() {

            public void windowClosing(WindowEvent e) {
                logger.trace("windowClosing "
                        + e.getSource().getClass().getName());
                if (timer != null) {
                    timer.cancel();
                    timer.purge();
                }
                if (com != null) {
                    com.closeSerialPort();
                }
            }
        });

        getContentPane().setLayout(new BorderLayout());
//      getContentPane().add(new JPanel(), BorderLayout.SOUTH);
        getContentPane().add(new MwMainPanel(getRawImuChartPanel(),getSettingsPanel()), BorderLayout.CENTER);

        pack();
    }

    
    private JPanel getSettingsPanel() {
        
        if (settingsPanel == null) {
            settingsPanel = new JPanel();
            settingsPanel.setLayout(new BorderLayout());
  
            JButton writeToEepromButton = new MwJButton("Write","Write to eeprom");
            writeToEepromButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    logger.trace("actionPerformed "
                            + e.getSource().getClass().getName());
                    //TODO
                }
            });
            
            JButton readFromEepromButton = new MwJButton("Read","Read eeprom");
            int[] req ={MSP.BOXNAMES, MSP.PIDNAMES, MSP.RC_TUNING, MSP.PID, MSP.BOX, MSP.MISC };
            readFromEepromButton.addActionListener(new actionMspSender(req));
            
            JButton calibGyrButton = new MwJButton("Gyro","Gyro calibration");
            JButton calibAccButton = new MwJButton("Acc","Acc calibration");
            JButton calibMagButton = new MwJButton("Mag","Mag calibration");
               
            calibAccButton.addActionListener(new actionMspSender(MSP.ACC_CALIBRATION));
            calibMagButton.addActionListener(new actionMspSender(MSP.MAG_CALIBRATION));
//          calibGyrButton.addActionListener(new actionMspSender(MSP.MAG_CALIBRATION));
            
            JPanel pane = new JPanel();
            pane.setLayout(new FlowLayout(FlowLayout.LEADING));
            pane.setBorder(new EmptyBorder(1, 1, 1, 1));

            JPanel pidPane =      new MwPIDPanel();
            MSP.setPidChangeListener((ChangeListener) pidPane);
            pane.add(pidPane);
            
            JPanel boxPane =  new MwBOXPanel();
            MSP.setBoxChangeListener((ChangeListener) boxPane);
            pane.add(boxPane);
            
            settingsPanel.add(pane, BorderLayout.CENTER);
            
            pane = new JPanel();
            pane.setLayout(new FlowLayout(FlowLayout.LEADING));
            pane.setBorder(new EmptyBorder(1, 1, 1, 1));
   
             pane.add(readFromEepromButton );
             pane.add(writeToEepromButton );
             pane.add(calibGyrButton );
             pane.add(calibAccButton );
             pane.add(calibMagButton );

            settingsPanel.add(pane, BorderLayout.SOUTH);

            
        }
        return settingsPanel;
    }

    protected static void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
        timer = null;

    }


    protected static void openSerialPort() {
                closeSerialPort();
                getSerialPortAsMenuItem();
                if (portNameMenuGroup.getSelection() == null) {
                    List<String> list = SerialDevice.getPortNameList();
                    if (list == null || list.size() == 0) {
                        list.add("");
                    }
                    Object[] array =list.toArray(new String[list.size()]);
                    String name = (String) JOptionPane.showInputDialog(
                            MwGuiFrame.getInstance(),
                            "Select a Serial Port", "port",
                            JOptionPane.INFORMATION_MESSAGE, null,
                            array , array[0]);

                    Enumeration<AbstractButton> els = portNameMenuGroup.getElements();
                    ButtonModel model = null;
                    while (els.hasMoreElements()) {
                        AbstractButton abstractButton = (AbstractButton) els
                                .nextElement();
                        try {
                            if (abstractButton.getActionCommand().equals(name)) {
                                model = abstractButton.getModel();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (model != null) {
                        portNameMenuGroup.setSelected(model, true);
                    } else {

                        JOptionPane.showMessageDialog(MwGuiFrame.getInstance(),
                                "Error while getting serial port name");
                        return;
                    }
                }
                try {
                    String portname = (String) (portNameMenuGroup.getSelection().getActionCommand());
                    if (portname == null ) {
                        return; // this should not happen, unless a bug
                    }
                    com = new SerialCom(portname,
                            (Integer) Integer.valueOf(baudRateMenuGroup.getSelection().getActionCommand()));
                    com.openSerialPort();
                    com.setListener(MwGuiFrame.getInstance());
                    
                    MwGuiFrame.getInstance().setTitle(new StringBuffer().append(portname).append("@").
                            append(baudRateMenuGroup.getSelection().getActionCommand()).toString() );
                } catch (SerialNotFoundException e) {

                } catch (SerialException e) {
                    e.printStackTrace();
                }
    }

    public void setTitle(String s){
        StringBuffer title = new StringBuffer().append(frameTitle);
        if (s != null && s.length()>0){
            title.append(" - ").append(s);
        }
        super.setTitle(title.toString());
    }
    
    protected static void restartTimer(Integer rate) {
        final class SerialTimeOut extends TimerTask {

            public void run() {
                try {
                    // TODO do no send all requests at the same time

                     send(MSP.request(MSP.ATTITUDE));
                     send(MSP.request(MSP.ALTITUDE));
                     
                    if (motorFrame!=null && motorFrame.isVisible()) {
                        send(MSP.request(MSP.MOTOR));
                    }
                    if (servoFrame!=null && servoFrame.isVisible()) {
                        send(MSP.request(MSP.SERVO));
                    }
                    send(MSP.request(MSP.RAW_IMU));
                } catch (Exception e) {
                    timer.cancel();
                    // timer.purge();
                }
            }

        }
        if (timer != null) {
            timer.cancel();
            timer.purge();

        }
        timer = new Timer();
        timer.schedule(new SerialTimeOut(), 10, 1000 / rate);
        defaultRefreshRate  = rate;
    }


    public static DebugFrame getDebugFrame() {
        if (debugFrame == null) {
            debugFrame = new DebugFrame("Debug serial");
        }
        return debugFrame;
    }

    protected static void showDebugFrame() {
        getDebugFrame().setVisible(true);
        getDebugFrame().repaint();

    }

    public static void closeDebugFrame() {
        if (debugFrame != null) {
            getDebugFrame().setVisible(false);
        }
    }

    public static SerialCom getCom() throws SerialException {
        if (com == null) {
            throw new SerialException("Serial Com is nul");
        }
        return com;
    }

    private JMenuBar createMenuBar() {
        
        JMenuBar menubar = new JMenuBar();
        /* différents menus */
        JMenu menu1 = new JMenu("File");
        JMenu menu2 = new JMenu("Edit");
        JMenu menu3 = new JMenu("View");
        JMenu menu4 = new JMenu("Serial");

        /* differents choix de chaque menu */
        JMenuItem motor = new JMenuItem("Motor");
        JMenuItem servo = new JMenuItem("Servo");
        JMenuItem consoleSerial = new JMenuItem("Console");

        JMenuItem quit = new JMenuItem("Quit");
        JMenuItem annuler = new JMenuItem("Undo");
        JMenuItem copier = new JMenuItem("Copy");
        JMenuItem coller = new JMenuItem("Paste");

        // JMenuItem openLog = new JMenuItem("Open");

        /* Ajouter les choix au menu */
        menu1.add(quit);
        
        menu2.add(annuler);
        menu2.add(copier);
        menu2.add(coller);
        
        menu3.add(servo);
        menu3.add(motor);

        menu4.add(getSerialPortAsMenuItem());
        menu4.add(getSerialBaudAsMenuItem());
        menu4.addSeparator();
      
        menu4.add(consoleSerial);
        
        /* Ajouter les menus  */
        menubar.add(menu1);
        menubar.add(menu2);
        menubar.add(menu3);
        menubar.add(menu4);
        

        consoleSerial.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                MwGuiFrame.showDebugFrame();
            }
        });

        servo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (servoFrame == null) {
                    servoFrame = new LogViewerFrame("Servo", MSP
                            .getRealTimeData(), MwSensorClassServo.class);
                } else {
                    servoFrame.setVisible(true);
                }
            }
        });

        motor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                if (motorFrame == null) {
                    motorFrame = new LogViewerFrame("Motor", MSP
                            .getRealTimeData(), MwSensorClassMotor.class);

                } else {
                    motorFrame.setVisible(true);
                }

            }
        });

      
        quit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                closeSerialPort();
                System.exit(0);
            }
        });

        // TODO about multiwii
        return menubar;
    }

    private JMenuItem getSerialBaudAsMenuItem() {
        JMenu m = new JMenu("Baud");
        baudRateMenuGroup = new ButtonGroup(  );
        for (Integer p :  SerialDevice.SERIAL_BAUD_RATE){
            JMenuItem sm = new JRadioButtonMenuItem(p.toString());
            sm.setActionCommand(p.toString());
            sm.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent event) {

                    logger.trace("actionPerformed "
                            + event.getSource().getClass().getName());

                    closeSerialPort();
                    try {
               
                            Object pp = event.getSource();
                            if (pp instanceof JRadioButtonMenuItem){
                                JRadioButtonMenuItem va = (JRadioButtonMenuItem) pp;
                               if (com != null){
                                com.setSerialRate(Integer.valueOf(va.getText()));
                                com.openSerialPort();
                                com.setListener(MwGuiFrame.getInstance());
                               }
                            } 
                        
                    } catch (SerialException e) {
                        e.printStackTrace();
                    }

                }
            });
            m.add(sm);
            baudRateMenuGroup.add(sm);
            if (DEFAULT_BAUDRATE.equals( p)){
                sm.setSelected(true);
            }
        }
        return m;
    }

    private static JMenu getSerialPortAsMenuItem() {
        if (serialMenuPort == null){
            JMenu m = new JMenu("Port");
            serialMenuPort =m;
        }else{
            serialMenuPort.removeAll();
        }
        
        portNameMenuGroup = new ButtonGroup( );
        for (String p : SerialDevice.getPortNameList()){
            JMenuItem sm = new JRadioButtonMenuItem(p);
            sm.setActionCommand(p);
            serialMenuPort.add(sm);
            portNameMenuGroup.add(sm);
        }
        serialMenuPort.addSeparator();
        serialMenuPort.add(getRescanSerialMenuIten());
        serialMenuPort.add(getDisconnectSerialMenuIten());
        return serialMenuPort;
    }


    private static JMenuItem getDisconnectSerialMenuIten() {
        if (disconnectSerial == null) {
            disconnectSerial = new JMenuItem("Close");

            disconnectSerial.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    closeSerialPort();
                    portNameMenuGroup.clearSelection();
                }
            });
        }
        return disconnectSerial;
    }

    private static JMenuItem getRescanSerialMenuIten() {
        if (rescanSerial == null) {
            rescanSerial = new JMenuItem("Rescan");
            rescanSerial.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    closeSerialPort();
                    getSerialPortAsMenuItem();
                }
            });
        }
        return rescanSerial;
    }

    /**
     * send a string to the serial com
     * @param s
     * @throws SerialException
     */
    synchronized private static void send(List<Byte> msp) throws SerialException {
        if (com != null) {
            byte[] arr = new byte[msp.size()];
            int i = 0;
            for (byte b: msp) {
             arr[i++] = b;
            }
            
            com.send(arr);
        }
    }

    /**
     * (non-Javadoc)
     * 
     * @see net.fd.gui.AbstractSerialMonitor#message(java.lang.String)
     */
    synchronized public void readSerialByte(final byte input) {

        MSP.decode(input);
        
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {

                if (getDebugFrame().isVisible()) {
                    debugFrame.readSerialByte(input);
                }
            }
        });
    }

    static void closeSerialPort() {
        if (com != null) {
            com.closeSerialPort();
        }
        stopTimer();
        com = null;
        MwGuiFrame.getInstance().setTitle(null);
    }

    @Override
    public void reportSerial(Throwable e) {
        // we have an error

                stopTimer();
                closeSerialPort();

    }

    public static void AddSensorCheckBox(String sensorName) {
        // TODO Auto-generated method stub
        getRealTimeCheckBowPanel().addSensorBox(sensorName);
    }

}
