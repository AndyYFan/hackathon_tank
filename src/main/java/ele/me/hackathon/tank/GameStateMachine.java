package ele.me.hackathon.tank; /**
 * Created by lanjiangang on 27/10/2017.
 */

import java.util.*;
import java.util.stream.Collectors;

public class GameStateMachine {
    private GameMap map;
    private Map<Integer, Tank> tanks;
    private List<Shell> shells = new LinkedList<>();
    private boolean flagExisting = false;
    private Position flagPos;
    private Map<String, Player> players;
    private String loser;
    private GameOptions options;

    public GameStateMachine(Map<Integer, Tank> tanks, GameMap map) {
        this.tanks = tanks;
        this.map = map;
    }

    public void newOrders(List<TankOrder> orders) {
        evaluateShellsMovement();
        evaluateFireActions(filtOrder(orders, "fire"));
        evaluateTurnDirectionActions(filtOrder(orders, "turnTo"));
        evaluateMoveActions(filtOrder(orders, "move"));
    }

    private void evaluateShellsMovement() {
        for (int i = 0; i < options.getShellSpeed(); i++) {
            shells.forEach(s -> s.moveOneStep());
            shells.forEach(shell -> {
                Position pos = shell.getPos();
                if (map.isBarrier(pos)) {
                    shell.destroyed();
                } else {
                    Tank tankAt = getTankAt(pos);
                    if (tankAt != null) {
                        shell.destroyed();
                        tankAt.hit();
                    }
                }
            });
            //evaluate result on each step
            clearDestroyedTargets();
        }
    }

    private void clearDestroyedTargets() {
        List<Tank> destroyedTanks = tanks.values().stream().filter(t -> t.isDestroyed()).collect(Collectors.toCollection(() -> new LinkedList<>()));
        destroyedTanks.forEach(t -> tanks.remove(t.getId()));

        shells.removeIf(shell -> shell.isDestroyed());
    }

    private LinkedList<TankOrder> filtOrder(List<TankOrder> orders, String orderName) {
        return orders.stream().filter(o -> orderName.equals(o.getOrder()) && isValidateOrder(o)).collect(Collectors.toCollection(() -> new LinkedList<>()));
    }

    private void evaluateFireActions(List<TankOrder> orders) {

        List<Shell> newShells = new LinkedList<>();
        //let all tanks fire first so as to simulate all tanks are acting in the SAME time .
        for (TankOrder order : orders) {
            if (!isValidateOrder(order))
                continue;

            Shell shell = tanks.get(order.getTankId()).fireAt(order.getParameter());
            if (shell != null) {
                System.out.println("Tank " + order.getTankId() + " fire a new shell :" + shell);
                newShells.add(shell);
            }
        }
        //then the state machine evaluate new shells's result.
        //thus even if a tank is destroyed by new fired shell, it still has a chance to fire a shell before it dies.
        for (Shell shell : newShells) {
            if (map.isBarrier(shell.getPos())) {
                shell.destroyed();
                continue;
            }
            Tank tankAt = getTankAt(shell.getPos());
            if (tankAt != null) {
                tankAt.hit();
                shell.destroyed();
            }
        }

        newShells.removeIf(Shell::isDestroyed);
        getShells().addAll(newShells);

        clearDestroyedTargets();
    }

    private Tank getTankAt(Position pos) {
        List<Tank> tanksAt = tanks.values().stream().filter(t -> t.getPos().equals(pos)).collect(Collectors.toList());
        if (tanksAt.size() > 1) {
            String msg = "Found more than one tank in same position: ";
            for (Tank t : tanksAt) {
                msg += t;
            }
            throw new InvalidState(msg);
        }
        return tanksAt.size() > 0 ? tanksAt.get(0) : null;
    }

    private void evaluateTurnDirectionActions(List<TankOrder> orders) {
        for (TankOrder order : orders) {
            if (!isValidateOrder(order))
                return;

            tanks.get(order.getTankId()).turnTo(order.getParameter());
        }
    }

    private boolean isValidateOrder(TankOrder order) {
        if (tanks.containsKey(order.getTankId()) && !tanks.get(order.getTankId()).isDestroyed())
            return true;

        return false;
    }

    private void evaluateMoveActions(List<TankOrder> orders) {
        Map<Tank, Position[]> moveTracks = new LinkedHashMap<>(tanks.size());
        orders.forEach(o -> {
            if (isValidateOrder(o)) {
                moveTracks.put(tanks.get(o.getTankId()), tanks.get(o.getTankId()).evaluateMoveTrack());
            }
        });

        int maxMoves = 0;
        for (Position[] p : moveTracks.values()) {
            if (p.length > maxMoves)
                maxMoves = p.length;
        }

        Map<Tank, Position> result = new LinkedHashMap<>();
        initResult(result);

        for (int i = 0; i < maxMoves; i++) {

            final int finalI = i;

            //check if tanks get overlapped
            List<Tank> overlappedTanks = checkOverlap(i, moveTracks);

            //check if tanks move into a barrier
            checkBarrier(i, moveTracks);

            //check if tank is destroyed by shells
            List<Tank> destroyedTanks = checkShells(i, moveTracks);
            destroyedTanks.forEach(t -> {
                result.remove(t);
                moveTracks.remove(t);
            });

            //record latest result
            moveTracks.forEach((tank, track) -> {
                result.put(tank, track[finalI]);
            });

            //check if any tank will get the flag
            checkFlag(result);

            //remove tank which already evaluates all its movements.
            //this will happen on condition that tanks have different speed.
            for (Iterator<Tank> itr = moveTracks.keySet().iterator(); itr.hasNext(); ) {
                Tank t = itr.next();
                if (moveTracks.get(t).length - 1 == i) {
                    itr.remove();
                }
            }

            clearDestroyedTargets();
        }

        //apply the result
        result.forEach((tank, pos) -> tank.moveTo(pos));
    }

    private void checkFlag(Map<Tank, Position> result) {
        if (flagExisting) {
            List<Map.Entry<Tank, Position>> tanks = result.entrySet().stream().filter(e -> e.getValue().equals(flagPos))
                    .collect(Collectors.toCollection(() -> new LinkedList<>()));
            if (tanks.size() > 0) {
                flagExisting = false;

                getPlayers().values().stream().filter(p -> p.belongTo(tanks.get(0).getKey())).forEach(p -> p.captureFlag());
            }
        }
    }

    private List<Tank> checkShells(int i, Map<Tank, Position[]> moveTracks) {
        List<Tank> tankList = new LinkedList<>();
        for (Tank t : moveTracks.keySet()) {
            List<Shell> shellList = getShellAt(moveTracks.get(t)[i]);

            shellList.forEach(s -> s.destroyed());

            if (shellList.size() >= t.getHp()) {
                t.destroyed();
                tankList.add(t);
            }
        }
        return tankList;
    }

    private List<Tank> checkBarrier(int i, Map<Tank, Position[]> moveTracks) {
        List<Tank> tankList = moveTracks.keySet().stream().filter(t -> map.isBarrier(moveTracks.get(t)[i]))
                .collect(Collectors.toCollection(() -> new LinkedList<>()));
        tankList.forEach(t -> moveTracks.remove(t));
        return tankList;

    }

    private List<Tank> checkOverlap(final int i, Map<Tank, Position[]> moveTracks) {
        List<Position> evaluatedPoss = moveTracks.values().stream().map(p -> p[i]).collect(Collectors.toCollection(() -> new LinkedList<Position>()));

        //add positions of standstill tanks
        List<Position> stillTanksPos = getTanks().values().stream().filter(t -> !moveTracks.containsKey(t)).map(t -> t.getPos())
                .collect(Collectors.toCollection(() -> new LinkedList<Position>()));
        evaluatedPoss.addAll(stillTanksPos);

        List<Tank> tankList = moveTracks.keySet().stream().filter(t -> Collections.frequency(evaluatedPoss, moveTracks.get(t)[i]) > 1)
                .collect(Collectors.toCollection(() -> new LinkedList<>()));
        tankList.forEach(t -> moveTracks.remove(t));
        return tankList;
    }

    private void initResult(Map<Tank, Position> result) {
        tanks.values().forEach(t -> result.put(t, t.getPos()));
    }

    private List<Shell> getShellAt(Position position) {
        List<Shell> shellList = shells.stream().filter(shell -> !shell.isDestroyed() && shell.getPos().equals(position))
                .collect(Collectors.toCollection(() -> new LinkedList<Shell>()));
        return shellList;
    }

    public List<Tank> getLeftTanks() {
        List<Tank> left = tanks.values().stream().filter(t -> !t.isDestroyed()).collect(Collectors.toCollection(() -> new LinkedList<Tank>()));
        return left;
    }

    public int getFlagNoByPlayer(String name) {
        return getPlayers().get(name).getNoOfFlag();
    }

    public Position generateFlag() {
        flagPos = new Position(map.size() / 2 + 1, map.size() / 2 + 1);
        flagExisting = true;
        return flagPos;
    }

    public Map<String, GameState> reportState() {
        return getPlayers().keySet().stream().collect(Collectors.toMap(name -> name, name -> generatePlayerState(name)));
    }

    private GameState generatePlayerState(String playerName) {
        GameState playerState = new GameState(playerName);

        //add own tanks
        getPlayers().get(playerName).getTanks().stream().filter(tankId -> tankExisting(tankId)).forEach(tankId -> {
            playerState.getTanks().add(getTanks().get(tankId));
        });

        //add enemy's tanks if they are visible.
        getPlayers().entrySet().stream().filter(e -> !e.getKey().equals(playerName)).forEach(e -> {
            e.getValue().getTanks().stream().filter(tankId -> tankVisible(tankId)).forEach(tankId -> {
                playerState.getTanks().add(getTanks().get(tankId));
            });
        });

        //all shells which are visible
        getShells().stream().filter(s -> map.isVisible(s.getPos())).forEach(s -> playerState.getShells().add(s));

        return playerState;

    }

    private boolean tankVisible(Integer tankId) {
        return tankExisting(tankId) && map.isVisible(getTanks().get(tankId).getPos());
    }

    private boolean tankExisting(Integer tankId) {
        return getTanks().containsKey(tankId) && !getTanks().get(tankId).isDestroyed();
    }

    public boolean gameOvered() {
        for (Player p : players.values()) {
            if (p.getTanks().stream().noneMatch(id -> tanks.containsKey(id) && !tanks.get(id).isDestroyed())) {
                System.out.println("Player " + p.getName() + " lose game because all his tanks are destroyed!");
                loser = p.getName();
                return true;
            }
        }
        return false;
    }

    public String getLoser() {
        return loser;
    }

    public Map<String, Integer> countScore(int tankScore, int flagScore) {
        return players.values().stream().collect(Collectors.toMap(p -> p.getName(), p -> {
            long score =
                    p.getTanks().stream().filter(id -> tanks.containsKey(id) && !tanks.get(id).isDestroyed()).count() * tankScore + p.getNoOfFlag() * flagScore;
            return (int) score;
        }));
    }

    public Map<Integer, Tank> getTanks() {
        return tanks;
    }

    protected List<Shell> getShells() {
        return shells;
    }

    public void setPlayers(Map<String, Player> players) {
        this.players = players;
    }

    public Map<String, Player> getPlayers() {
        return players;
    }

    public GameOptions getOptions() {
        return options;
    }

    public void setOptions(GameOptions options) {
        this.options = options;
    }
}
