import core.game.Game;
import core.game.GameResult;
import core.game.timer.StopwatchCPU;
import core.game.ui.Configuration;
import core.match.GameEvent;
import core.player.Player;

/**
 * 六子棋AI评测程序
 */
public class AiEvaluator {
    public static void main(String[] args) throws CloneNotSupportedException {
        StopwatchCPU timer = new StopwatchCPU();
        zeroCarnival(); //随机棋手大狂欢
        //oucLeague(); //海之子联赛
        //oneMatch();    //自组织一场比赛（两个棋手先后手各下一局，共下两局棋）
        double elapsedTime = timer.elapsedTime();
        System.out.printf("%.4f", elapsedTime);
    }

    /**
     * 这个用来完成项目二的第二部分内容，随机棋手的测试。
     *
     */
    private static void zeroCarnival(){
        //Zero大狂欢:)
        Configuration.GUI = false; //不使用GUI
        GameEvent event = new GameEvent("Carnival of Zeros");
        //每对棋手下500局棋，先后手各250局
        //n个棋手，共下C(n,2)*500局棋，每个棋手下500*(n-1)局棋
        event.carnivalRun(500);
        event.showResults();
    }

    //海之子联赛
    private static void oucLeague() throws CloneNotSupportedException {

        Configuration.GUI = false; //使用GUI
        Configuration.STEP_INTER = 500;
        GameEvent event = new GameEvent("海之子排名赛");

        //主场先手与其他棋手对局
        event.hostGames(Configuration.HOST_ID);

        event.showHostResults(Configuration.HOST_ID);
    }
    //自组织一场比赛
    private static void oneMatch(){
        Configuration.GUI = true;
        Configuration.STEP_INTER = 500;
        Player one = new stud.g01.AI();
        Player two = new stud.g07.AI();
        Game game = new Game(one, two);
        game.start();
        System.out.flush();
        while (game.running()){
            ;
        }
        for (GameResult result : one.gameResults()){
            System.out.println(result);
        }
        System.out.flush();
    }
}

