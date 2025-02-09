import java.util.ArrayList;  //
import javax.swing.JOptionPane;

public class MCAI extends AI {
    private int aiplayer = 1;
    private int minLen = 49;
    private Location lastPlayed;
    public int diffLevel = 75;
    private static final int SIMULATION_TIME = 4000;

    private class Node {
        int[][] board;
        Location move;
        int visits;
        double wins;
        ArrayList<Node> children;
        Node parent;
        int playerToMove;

        Node(int[][] board, Location move, Node parent, int playerToMove) {
            this.board = Board.BoardCopy(board);
            this.move = move;
            this.visits = 0;
            this.wins = 0;
            this.children = new ArrayList<>();
            this.parent = parent;
            this.playerToMove = playerToMove;
        }

        Node getBestChild() {
            Node best = null;
            double bestValue = Double.NEGATIVE_INFINITY;
            
            for (Node child : children) {
                if (child.visits == 0) {
                    return child;
                }
                double ucb1 = (child.wins / child.visits) + 
                             Math.sqrt(2 * Math.log(this.visits) / child.visits);
                if (ucb1 > bestValue) {
                    bestValue = ucb1;
                    best = child;
                }
            }
            return best;
        }
    }

    public MCAI() {}

    public MCAI(int player) {
        aiplayer = player;
    }

    public int getPlayerCode() {
        return aiplayer;
    }

    private int calcN(int[][] board, int player, Location l, ArrayList<Location> visited, int count) {
        if (count<minLen && ((player==1 && l.y==6) || (player==2 && l.x==6))) {
            minLen=count;
            return count;
        } else if ((player==1 && l.y==6) || (player==2 && l.x==6)) {
            return count;
        }

        if (player==1 && count+(6-l.y)>=minLen) {
            return 999;
        } else if (player==2 && count+(6-l.x)>=minLen) {
            return 999;
        }

        ArrayList<Location> adj = l.getAdjacentLocations();
        ArrayList<Bridge> bridges = l.getBridges();
        ArrayList<Location> v = Utils.ALCopy(visited);
        v.add(new Location(l.x, l.y));
        int min = 999;

        for (int i=0; i<bridges.size(); i++) {
            Bridge b = bridges.get(i);
            boolean canUseBridge = board[b.mids.get(0).y][b.mids.get(0).x]==0 && 
                                 board[b.mids.get(1).y][b.mids.get(1).x]==0;
            if (canUseBridge && !Utils.ALContains(v, b.l1) && 
                (board[b.l1.y][b.l1.x]==player || board[b.l1.y][b.l1.x]==0)) {
                int val = calcN(board, player, b.l1, v, count);
                if (val<min) {
                    min=val;
                }
            }
        }

        for (int i=0; i<adj.size(); i++) {
            Location loc = adj.get(i);
            if (!Utils.ALContains(v, loc) && (board[loc.y][loc.x]==player || board[loc.y][loc.x]==0)) {
                int val = 999;
                if (board[loc.y][loc.x]==player) {
                    val = calcN(board, player, loc, v, count);
                } else {
                    val = calcN(board, player, loc, v, count+1);
                }
                if (val<min) {
                    min=val;
                }
            }
        }
        return min;
    }

    public double calcVal(int[][] board) {
        int opp = aiplayer==1 ? 2 : 1;
        minLen = 49;
        double maxno = 999;

        for (int i=0; i<board.length; i++) {
            if (board[i][0]!=opp) {
                int initCountO = board[i][0]==opp ? 0 : 1;
                Location oLoc = aiplayer==1 ? new Location(0, i) : new Location(i, 0);
                
                double no = (double)calcN(board, opp, oLoc, new ArrayList<Location>(), initCountO);
                minLen = 49;
                if (no<maxno) {
                    maxno = no;
                }
            }
        }
        return maxno;
    }

    private ArrayList<Location> getPossibleMoves(int[][] board) {
        ArrayList<Location> moves = new ArrayList<>();
        for (int y=0; y<board.length; y++) {
            for (int x=0; x<board[y].length; x++) {
                if (board[y][x] == Constants.EMPTY) {
                    moves.add(new Location(x, y));
                }
            }
        }
        return moves;
    }

    private void makeMove(int[][] board, Location move, int player) {
        board[move.y][move.x] = player;
    }

    private Node select(Node node) {
        while (!getPossibleMoves(node.board).isEmpty() && !node.children.isEmpty()) {
            node = node.getBestChild();
            if (node == null) break;
        }
        return node;
    }

    private Node expand(Node node) {
        ArrayList<Location> moves = getPossibleMoves(node.board);
        for (Location move : moves) {
            int[][] newBoard = Board.BoardCopy(node.board);
            makeMove(newBoard, move, node.playerToMove);
            Node child = new Node(
                newBoard, 
                move, 
                node, 
                node.playerToMove == Constants.WHITE ? Constants.BLACK : Constants.WHITE
            );
            node.children.add(child);
        }
        return !node.children.isEmpty() ? node.children.get(0) : node;
    }

    private double simulate(Node node) {
        int[][] simBoard = Board.BoardCopy(node.board);
        int currentPlayer = node.playerToMove;
        
        while (Board.hasEmpty(simBoard)) {
            ArrayList<Location> moves = getPossibleMoves(simBoard);
            if (moves.isEmpty()) break;
            
            Location move = moves.get((int)(Math.random() * moves.size()));
            makeMove(simBoard, move, currentPlayer);
            
            double val = calcVal(simBoard);
            if (val > Math.pow(simBoard.length, 2)) {
                return currentPlayer == aiplayer ? 0 : 1;
            }
            
            currentPlayer = (currentPlayer == Constants.WHITE) ? Constants.BLACK : Constants.WHITE;
        }
        return 0.5;
    }

    private void backpropagate(Node node, double result) {
        while (node != null) {
            node.visits++;
            node.wins += result;
            node = node.parent;
            result = 1 - result;
        }
    }

    @Override
    public Location getPlayLocation(int[][] board, Location last) {
        long startTime = System.currentTimeMillis();
        lastPlayed = last;

        if (last == null || last.x == -1) {
            return new Location(board.length/2, board.length/2);
        }

        Node root = new Node(board, last, null, aiplayer);
        
        while (System.currentTimeMillis() - startTime < SIMULATION_TIME) {
            Node selected = select(root);
            Node expanded = expand(selected);
            double result = simulate(expanded);
            backpropagate(expanded, result);
        }

        Node bestChild = null;
        int maxVisits = -1;
        
        for (Node child : root.children) {
            if (child.visits > maxVisits) {
                maxVisits = child.visits;
                bestChild = child;
            }
        }

        if (bestChild == null) {
            ArrayList<Location> adjacent = last.getAdjacentLocations();
            for (Location loc : adjacent) {
                if (loc.x >= 0 && loc.x < board.length && 
                    loc.y >= 0 && loc.y < board.length && 
                    board[loc.y][loc.x] == Constants.EMPTY) {
                    return loc;
                }
            }
            return getPossibleMoves(board).get(0);
        }

        return bestChild.move;
    }
}
