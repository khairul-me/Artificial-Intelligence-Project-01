import java.util.ArrayList;
import javax.swing.JOptionPane;

public class MCAI extends AI {
    private int aiplayer = 1;
    private int minLen = 49;
    private Location lastPlayed;
    private static final int BASE_SIMULATION_TIME = 1000;
    private static final int MAX_SIMULATION_DEPTH = 12;
    private static final int MAX_CHILDREN = 8;
    
    // Cache for frequently accessed data
    private static ArrayList<Bridge>[][] bridgeCache = new ArrayList[7][7];
    private static final int[][] distanceFromCenter = precomputeDistances();
    
    private static int[][] precomputeDistances() {
        int[][] distances = new int[7][7];
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 7; x++) {
                distances[y][x] = Math.max(Math.abs(3-x), Math.abs(3-y));
            }
        }
        return distances;
    }

    private class Node {
        int[][] board;
        Location move;
        int visits;
        double wins;
        ArrayList<Node> children;
        Node parent;
        int playerToMove;
        double cachedUcbValue;
        boolean needsUcbUpdate;

        Node(int[][] board, Location move, Node parent, int playerToMove) {
            this.board = Board.BoardCopy(board);
            this.move = move;
            this.visits = 0;
            this.wins = 0;
            this.children = new ArrayList<>();
            this.parent = parent;
            this.playerToMove = playerToMove;
            this.needsUcbUpdate = true;
        }

        Node getBestChild() {
            Node best = null;
            double bestValue = Double.NEGATIVE_INFINITY;
            double logVisits = Math.log(this.visits);
            
            for (Node child : children) {
                if (child.visits == 0) return child;
                
                if (child.needsUcbUpdate) {
                    double exploitation = child.wins / child.visits;
                    double exploration = Math.sqrt(2 * logVisits / child.visits);
                    child.cachedUcbValue = exploitation + exploration;
                    child.needsUcbUpdate = false;
                }
                
                if (child.cachedUcbValue > bestValue) {
                    bestValue = child.cachedUcbValue;
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
        // Early exit optimizations
        if (count >= minLen) return 999;
        
        int directDistance = player == 1 ? (6 - l.y) : (6 - l.x);
        if (count + directDistance >= minLen) return 999;
        
        if (count < minLen && ((player==1 && l.y==6) || (player==2 && l.x==6))) {
            minLen = count;
            return count;
        } else if ((player==1 && l.y==6) || (player==2 && l.x==6)) {
            return count;
        }

        ArrayList<Location> adj = l.getAdjacentLocations();
        ArrayList<Bridge> bridges = l.getBridges();
        ArrayList<Location> v = Utils.ALCopy(visited);
        v.add(new Location(l.x, l.y));
        int min = 999;

        // Check bridges first for efficiency
        for (Bridge b : bridges) {
            if (board[b.mids.get(0).y][b.mids.get(0).x] == 0 && 
                board[b.mids.get(1).y][b.mids.get(1).x] == 0 &&
                !Utils.ALContains(v, b.l1) && 
                (board[b.l1.y][b.l1.x] == player || board[b.l1.y][b.l1.x] == 0)) {
                min = Math.min(min, calcN(board, player, b.l1, v, count));
            }
        }

        if (min == 999) { // Only check adjacent if no good bridge found
            for (Location loc : adj) {
                if (!Utils.ALContains(v, loc) && 
                    (board[loc.y][loc.x] == player || board[loc.y][loc.x] == 0)) {
                    int val = board[loc.y][loc.x] == player ? 
                             calcN(board, player, loc, v, count) :
                             calcN(board, player, loc, v, count+1);
                    min = Math.min(min, val);
                }
            }
        }
        
        return min;
    }

    public double calcVal(int[][] board) {
        int opp = aiplayer == 1 ? 2 : 1;
        minLen = 49;
        double maxno = 999;

        for (int i = 0; i < board.length; i++) {
            if (board[i][0] != opp) {
                int initCountO = board[i][0] == opp ? 0 : 1;
                Location oLoc = aiplayer == 1 ? new Location(0, i) : new Location(i, 0);
                maxno = Math.min(maxno, calcN(board, opp, oLoc, new ArrayList<>(), initCountO));
                minLen = 49;
            }
        }
        return maxno;
    }

    private ArrayList<Location> getPossibleMoves(int[][] board) {
        ArrayList<Location> moves = new ArrayList<>();
        
        // Prioritize moves by distance from center
        for (int d = 0; d <= 3; d++) {
            for (int y = 0; y < board.length; y++) {
                for (int x = 0; x < board[y].length; x++) {
                    if (board[y][x] == Constants.EMPTY && 
                        distanceFromCenter[y][x] == d) {
                        moves.add(new Location(x, y));
                    }
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

    private int evaluateMove(Location move, int[][] board) {
        int score = 0;
        
        // Center control
        score -= 2 * distanceFromCenter[move.y][move.x];
        
        // Bridge evaluation
        ArrayList<Bridge> bridges = move.getBridges();
        for (Bridge b : bridges) {
            // Check if bridge connects to our pieces
            if (board[b.l1.y][b.l1.x] == aiplayer || 
                board[b.l2.y][b.l2.x] == aiplayer) {
                score += 3;  // Connected bridge
            }
            // Check if bridge blocks opponent
            else if (board[b.l1.y][b.l1.x] == (aiplayer == 1 ? 2 : 1) || 
                     board[b.l2.y][b.l2.x] == (aiplayer == 1 ? 2 : 1)) {
                score += 4;  // Blocking bridge
            }
        }
        
        // Edge control
        if (aiplayer == 1 && (move.y == 0 || move.y == 6)) {
            score += 2;
        } else if (aiplayer == 2 && (move.x == 0 || move.x == 6)) {
            score += 2;
        }
        
        return score;
    }

    private Node expand(Node node) {
        ArrayList<Location> moves = getPossibleMoves(node.board);
        if (moves.isEmpty()) return node;

        moves.sort((a, b) -> evaluateMove(b, node.board) - evaluateMove(a, node.board));
        
        int numChildren = Math.min(MAX_CHILDREN, moves.size());
        for (int i = 0; i < numChildren; i++) {
            Location move = moves.get(i);
            int[][] newBoard = Board.BoardCopy(node.board);
            makeMove(newBoard, move, node.playerToMove);
            node.children.add(new Node(
                newBoard, 
                move, 
                node, 
                node.playerToMove == Constants.WHITE ? Constants.BLACK : Constants.WHITE
            ));
        }
        return !node.children.isEmpty() ? node.children.get(0) : node;
    }

    private double simulate(Node node) {
        int[][] simBoard = Board.BoardCopy(node.board);
        int currentPlayer = node.playerToMove;
        int depth = 0;
        int moveCount = countMoves(simBoard);
        
        // Adjust simulation depth based on game phase
        int maxDepth = moveCount > 30 ? 15 : 
                      moveCount < 10 ? 8 : 
                      MAX_SIMULATION_DEPTH;
        
        // Quick win check
        double initialVal = calcVal(simBoard);
        if (initialVal > Math.pow(simBoard.length, 2)) {
            return currentPlayer == aiplayer ? 0 : 1;
        }
        
        while (Board.hasEmpty(simBoard) && depth < maxDepth) {
            ArrayList<Location> moves = getPossibleMoves(simBoard);
            if (moves.isEmpty()) break;
            
            Location move = null;
            if (Math.random() < 0.5) { // Increased strategic move probability
                int bestScore = Integer.MIN_VALUE;
                int movsToCheck = Math.min(moves.size(), 5);
                for (int i = 0; i < movsToCheck; i++) {
                    Location m = moves.get(i);
                    int score = evaluateMove(m, simBoard);
                    if (score > bestScore) {
                        bestScore = score;
                        move = m;
                    }
                }
            }
            if (move == null) {
                move = moves.get((int)(Math.random() * moves.size()));
            }
            
            makeMove(simBoard, move, currentPlayer);
            
            double val = calcVal(simBoard);
            if (val > Math.pow(simBoard.length, 2)) {
                return currentPlayer == aiplayer ? 0 : 1;
            }
            
            currentPlayer = (currentPlayer == Constants.WHITE) ? Constants.BLACK : Constants.WHITE;
            depth++;
        }
        
        return 1.0 / (1.0 + Math.exp(calcVal(simBoard) - 15));
    }

    private void backpropagate(Node node, double result) {
        while (node != null) {
            node.visits++;
            node.wins += result;
            node.needsUcbUpdate = true;
            node = node.parent;
            result = 1 - result;
        }
    }

    private Location getOpeningBookMove(int[][] board, Location last) {
        if (last == null || last.x == -1) {
            return new Location(board.length/2, board.length/2);
        }
        
        if (countMoves(board) == 1) {
            // If opponent didn't take center, take it
            if (board[3][3] == Constants.EMPTY) {
                return new Location(3, 3);
            }
            // Otherwise take adjacent position
            return new Location(3, 4);
        }
        
        return null;
    }

    private int countMoves(int[][] board) {
        int count = 0;
        for (int[] row : board) {
            for (int cell : row) {
                if (cell != Constants.EMPTY) count++;
            }
        }
        return count;
    }

    @Override
    public Location getPlayLocation(int[][] board, Location last) {
        long startTime = System.currentTimeMillis();
        lastPlayed = last;

        // Check opening book first
        Location bookMove = getOpeningBookMove(board, last);
        if (bookMove != null) return bookMove;

        // Get all valid moves
        ArrayList<Location> validMoves = getPossibleMoves(board);
        if (validMoves.isEmpty()) return null;

        // Dynamic time management
        int moveCount = countMoves(board);
        int timeLimit = moveCount < 10 ? 700 :    // Early game: faster
                       moveCount > 30 ? 1200 :    // Late game: more careful
                       BASE_SIMULATION_TIME;       // Mid game: normal

        Node root = new Node(board, last, null, aiplayer);
        
        // Run MCTS with time limit
        while (System.currentTimeMillis() - startTime < timeLimit) {
            Node selected = select(root);
            Node expanded = expand(selected);
            double result = simulate(expanded);
            backpropagate(expanded, result);
        }

        // Find best move
        Node bestChild = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (Node child : root.children) {
            if (child.visits == 0) continue;
            double score = child.wins / child.visits;
            if (score > bestScore && board[child.move.y][child.move.x] == Constants.EMPTY) {
                bestScore = score;
                bestChild = child;
            }
        }

        // Fallback strategies
        if (bestChild == null) {
            ArrayList<Location> adjacent = last.getAdjacentLocations();
            for (Location loc : adjacent) {
                if (loc.x >= 0 && loc.x < board.length && 
                    loc.y >= 0 && loc.y < board.length && 
                    board[loc.y][loc.x] == Constants.EMPTY) {
                    return loc;
                }
            }
            return validMoves.get(0);
        }

        return bestChild.move;
    }
}
