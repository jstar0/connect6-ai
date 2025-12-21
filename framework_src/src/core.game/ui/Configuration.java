package core.game.ui;


import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Configuration {
    public static final int TIME_LIMIT;
    public static boolean GUI;
    public static final int MAX_STEP;
    public static final String PLAYER_IDs;
    public static final int HOST_ID;
    public static final String GUI_TYPE;
    public static int STEP_INTER;   //每一步停留的时间

    public Configuration() {
    }

    static {
        Properties pps = new Properties();

        try {
            pps.load(new FileInputStream("file.properties"));
        } catch (IOException var2) {
            var2.printStackTrace();
        }

        TIME_LIMIT = Integer.parseInt(pps.getProperty("TimeLimit"));
        GUI = Boolean.parseBoolean(pps.getProperty("GUI"));
        MAX_STEP = Integer.parseInt(pps.getProperty("MaxStep"));
        STEP_INTER = Integer.parseInt(pps.getProperty("Step_Inter"));
        PLAYER_IDs = pps.getProperty("Player_Ids");
        HOST_ID = Integer.parseInt(pps.getProperty("Host"));
        GUI_TYPE = pps.getProperty("GuiType");
    }
}
