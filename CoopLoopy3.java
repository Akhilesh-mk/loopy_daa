import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class CoopLoopy3 extends JPanel {

    static class Node {
        int r, c, id;
        List<Edge> connectedEdges = new ArrayList<>();
        int filledCount = 0;
        int crossCount = 0;
        int unknownCount = 0;

        public Node(int r, int c, int id) { this.r = r; this.c = c; this.id = id; }
        public void initCache() { this.unknownCount = connectedEdges.size(); }

        public void updateCache(int oldState, int newState) {
            if (oldState == 0) unknownCount--; else if (oldState == 1) filledCount--; else if (oldState == 2) crossCount--;
            if (newState == 0) unknownCount++; else if (newState == 1) filledCount++; else if (newState == 2) crossCount++;
        }
    }

    static class Cell {
        int r, c, clue;
        List<Edge> edges = new ArrayList<>();
        int filledCount = 0;
        int crossCount = 0;
        int unknownCount = 4;

        public Cell(int r, int c, int clue) { this.r = r; this.c = c; this.clue = clue; }

        public void updateCache(int oldState, int newState) {
            if (oldState == 0) unknownCount--; else if (oldState == 1) filledCount--; else if (oldState == 2) crossCount--;
            if (newState == 0) unknownCount++; else if (newState == 1) filledCount++; else if (newState == 2) crossCount++;
        }

        public Edge getTop() { for(Edge e:edges) if(e.isHorizontal && e.n1.r == r) return e; return null; }
        public Edge getBottom() { for(Edge e:edges) if(e.isHorizontal && e.n1.r == r+1) return e; return null; }
        public Edge getLeft() { for(Edge e:edges) if(!e.isHorizontal && e.n1.c == c) return e; return null; }
        public Edge getRight() { for(Edge e:edges) if(!e.isHorizontal && e.n1.c == c+1) return e; return null; }
    }

    static class Edge {
        Node n1, n2;
        List<Cell> connectedCells = new ArrayList<>();
        boolean isHorizontal;
        int state; 
        private static final int HIT_TOLERANCE = 15; 

        public Edge(Node n1, Node n2, boolean isHorizontal) {
            this.n1 = n1; this.n2 = n2; this.isHorizontal = isHorizontal; this.state = 0;
        }

        public void setState(int newState) {
            if (this.state == newState) return;
            int oldState = this.state;
            this.state = newState;
            n1.updateCache(oldState, newState);
            n2.updateCache(oldState, newState);
            for (Cell c : connectedCells) c.updateCache(oldState, newState);
        }

        public boolean contains(int x, int y, int gap, int offsetX, int offsetY) {
            int x1 = n1.c * gap + offsetX; int y1 = n1.r * gap + offsetY;
            if (isHorizontal) return x >= x1 + 10 && x <= x1 + gap - 10 && y >= y1 - HIT_TOLERANCE && y <= y1 + HIT_TOLERANCE;
            else return x >= x1 - HIT_TOLERANCE && x <= x1 + HIT_TOLERANCE && y >= y1 + 10 && y <= y1 + gap - 10;
        }
    }

    // ==========================================
    // 2. NESTED AI LOGIC 
    // ==========================================

    static class ComputerPartner {
        
        // --- PILLAR 1: DIVIDE & CONQUER (MERGE SORT ON EDGES) ---
        private void customMergeSortEdges(List<Edge> list, int left, int right) {
            if (left >= right) return;
            int mid = left + (right - left) / 2;
            customMergeSortEdges(list, left, mid);
            customMergeSortEdges(list, mid + 1, right);
            mergeEdges(list, left, mid, right);
        }

        private void mergeEdges(List<Edge> list, int left, int mid, int right) {
            List<Edge> leftHalf = new ArrayList<>();
            List<Edge> rightHalf = new ArrayList<>();
            for (int i = left; i <= mid; i++) leftHalf.add(list.get(i));
            for (int i = mid + 1; i <= right; i++) rightHalf.add(list.get(i));

            int i = 0, j = 0, k = left;
            while (i < leftHalf.size() && j < rightHalf.size()) {
                // Descending order: highest weight first
                if (getGreedyWeight(leftHalf.get(i)) >= getGreedyWeight(rightHalf.get(j))) {
                    list.set(k++, leftHalf.get(i++));
                } else {
                    list.set(k++, rightHalf.get(j++));
                }
            }
            while (i < leftHalf.size()) list.set(k++, leftHalf.get(i++));
            while (j < rightHalf.size()) list.set(k++, rightHalf.get(j++));
        }

        // --- THE OBJECTIVE FUNCTION (WEIGHTS) ---
        private int getGreedyWeight(Edge e) {
            int weight = 0;
            // Rule 1: Clue Proximity
            for (Cell c : e.connectedCells) {
                if (c.clue == 0) weight -= 1000; // Toxic edge!
                else if (c.clue != -1) weight += (c.clue * 10);
            }
            // Rule 2: Path Continuation
            if (e.n1.filledCount == 1) weight += 15;
            if (e.n2.filledCount == 1) weight += 15;
            
            return weight;
        }

        // --- NEW PILLAR 2: PURE TEXTBOOK GREEDY ---
        private String applyTextbookGreedy(List<Edge> edges, List<Node> nodes, List<Cell> cells) {
            List<Edge> emptyEdges = new ArrayList<>();
            for (Edge e : edges) {
                if (e.state == 0) emptyEdges.add(e);
            }
            if (emptyEdges.isEmpty()) return null;

            // Sort edges greedily by objective function weight
            customMergeSortEdges(emptyEdges, 0, emptyEdges.size() - 1);

            // Greedily pick the highest scoring edge
            Edge bestEdge = emptyEdges.get(0);

            // Local Feasibility Check
            bestEdge.setState(1); // Tentatively place a line
            boolean isFeasible = true;
            
            if (bestEdge.n1.filledCount > 2 || bestEdge.n2.filledCount > 2) isFeasible = false;
            for (Cell c : bestEdge.connectedCells) {
                if (c.clue != -1 && c.filledCount > c.clue) isFeasible = false;
            }

            // Irrevocable Choice
            if (!isFeasible) {
                bestEdge.setState(2); // Must cross
                return "AI (Pure Greedy): Rejected Line. Placed CROSS.";
            } else {
                return "AI (Pure Greedy): Placed LINE on highest weight edge (Weight: " + getGreedyWeight(bestEdge) + ").";
            }
        }

        // --- AI EXECUTION ENGINE ---
        public String makeMove(List<Cell> cells, List<Node> nodes, List<Edge> edges) {
            String patternMove = applyGreedyPatterns(cells);
            if (patternMove != null) return patternMove;

            // Triggering the true textbook greedy algorithm
            String greedyMove = applyTextbookGreedy(edges, nodes, cells);
            if (greedyMove != null) return greedyMove;

            String dsMove = applyDomainSplitting(cells, nodes, edges);
            if (dsMove != null) return dsMove;

            return "AI: I've made all logical deductions. Your turn!";
        }

        private boolean setEdges(Edge... edgesToSet) {
            boolean changed = false;
            for (Edge e : edgesToSet) {
                if (e != null && e.state == 0) { e.setState(1); changed = true; }
            }
            return changed;
        }

        private String applyGreedyPatterns(List<Cell> cells) {
            for (Cell c : cells) {
                if (c.clue == 3) {
                    for (Cell neighbor : cells) {
                        if (neighbor.clue == 0) {
                            if (neighbor.r == c.r - 1 && neighbor.c == c.c - 1 && setEdges(c.getTop(), c.getLeft())) return "AI: Grandmaster Move! Diagonal 3 & 0.";
                            if (neighbor.r == c.r - 1 && neighbor.c == c.c + 1 && setEdges(c.getTop(), c.getRight())) return "AI: Grandmaster Move! Diagonal 3 & 0.";
                            if (neighbor.r == c.r + 1 && neighbor.c == c.c - 1 && setEdges(c.getBottom(), c.getLeft())) return "AI: Grandmaster Move! Diagonal 3 & 0.";
                            if (neighbor.r == c.r + 1 && neighbor.c == c.c + 1 && setEdges(c.getBottom(), c.getRight())) return "AI: Grandmaster Move! Diagonal 3 & 0.";
                        }
                        if (neighbor.clue == 3) {
                            if (neighbor.r == c.r && neighbor.c == c.c + 1 && setEdges(c.getLeft(), c.getRight(), neighbor.getRight())) return "AI: Adjacent 3s pattern.";
                            if (neighbor.c == c.c && neighbor.r == c.r + 1 && setEdges(c.getTop(), c.getBottom(), neighbor.getBottom())) return "AI: Adjacent 3s pattern.";
                            if (neighbor.r == c.r + 1 && neighbor.c == c.c + 1 && setEdges(c.getTop(), c.getLeft(), neighbor.getBottom(), neighbor.getRight())) return "AI: Diagonal 3s pattern.";
                            if (neighbor.r == c.r + 1 && neighbor.c == c.c - 1 && setEdges(c.getTop(), c.getRight(), neighbor.getBottom(), neighbor.getLeft())) return "AI: Diagonal 3s pattern.";
                        }
                    }
                    if (c.r == 0 && c.c == 0 && setEdges(c.getTop(), c.getLeft())) return "AI: Corner 3 forced outer lines.";
                    if (c.r == 0 && c.c == 4 && setEdges(c.getTop(), c.getRight())) return "AI: Corner 3 forced outer lines.";
                    if (c.r == 4 && c.c == 0 && setEdges(c.getBottom(), c.getLeft())) return "AI: Corner 3 forced outer lines.";
                    if (c.r == 4 && c.c == 4 && setEdges(c.getBottom(), c.getRight())) return "AI: Corner 3 forced outer lines.";
                }
            }
            return null;
        }

        private String applyDomainSplitting(List<Cell> cells, List<Node> nodes, List<Edge> edges) {
            for (Edge e : edges) {
                if (e.state == 0) {
                    if (!testHypothesis(e, 1, cells, nodes, edges)) {
                        e.setState(2); 
                        return "AI (Lookahead): Proved edge must be an 'X'.";
                    }
                    if (!testHypothesis(e, 2, cells, nodes, edges)) {
                        e.setState(1); 
                        return "AI (Lookahead): Proved edge must be a Line.";
                    }
                }
            }
            return null;
        }

        private boolean testHypothesis(Edge testEdge, int testState, List<Cell> cells, List<Node> nodes, List<Edge> edges) {
            int[] backup = new int[edges.size()];
            for(int i = 0; i < edges.size(); i++) backup[i] = edges.get(i).state;

            testEdge.setState(testState); 
            boolean isValid = true;
            boolean changed = true;
            
            while (changed && isValid) {
                changed = false;
                for(Cell c : cells) {
                    if(c.clue == -1) continue;
                    int f = c.filledCount; int u = c.unknownCount;
                    if (f > c.clue || f + u < c.clue) { isValid = false; break; }
                    if (u > 0) {
                        if (f == c.clue) { for(Edge ce : c.edges) if(ce.state == 0) { ce.setState(2); changed = true; } } 
                        else if (f + u == c.clue) { for(Edge ce : c.edges) if(ce.state == 0) { ce.setState(1); changed = true; } }
                    }
                }
                if(!isValid) break;

                for(Node n : nodes) {
                    int f = n.filledCount; int u = n.unknownCount;
                    if (f > 2 || (f == 1 && u == 0)) { isValid = false; break; }
                    if (u > 0) {
                        if (f == 2) { for(Edge ne : n.connectedEdges) if(ne.state == 0) { ne.setState(2); changed = true; } } 
                        else if (f == 1 && u == 1) { for(Edge ne : n.connectedEdges) if(ne.state == 0) { ne.setState(1); changed = true; } } 
                        else if (f == 0 && u == 1) { for(Edge ne : n.connectedEdges) if(ne.state == 0) { ne.setState(2); changed = true; } }
                    }
                }
                if(!isValid) break;
                
                if (hasPrematureLoop(edges, nodes, cells)) { isValid = false; break; }
            }

            for(int i = 0; i < edges.size(); i++) if (edges.get(i).state != backup[i]) edges.get(i).setState(backup[i]);
            return isValid;
        }

        private boolean hasPrematureLoop(List<Edge> edges, List<Node> nodes, List<Cell> cells) {
            int totalLines = 0;
            for (Edge e : edges) if (e.state == 1) totalLines++;

            Set<Node> visited = new HashSet<>();
            for (Node n : nodes) {
                if (n.filledCount > 0 && !visited.contains(n)) {
                    Set<Node> component = new HashSet<>();
                    boolean hasCycle = dfsFindCycle(n, null, component);
                    visited.addAll(component);
                    
                    if (hasCycle) {
                        int loopLines = 0;
                        for (Edge e : edges) {
                            if (e.state == 1 && component.contains(e.n1) && component.contains(e.n2)) {
                                loopLines++;
                            }
                        }
                        if (loopLines < totalLines) return true; 
                        
                        if (!dncAreCellsFinished(cells, 0, cells.size() - 1)) return true;
                    }
                }
            }
            return false;
        }

        private boolean dfsFindCycle(Node curr, Node prev, Set<Node> visited) {
            visited.add(curr);
            for (Edge e : curr.connectedEdges) {
                if (e.state == 1) {
                    Node next = (e.n1 == curr) ? e.n2 : e.n1;
                    if (next == prev) continue;
                    if (visited.contains(next)) return true;
                    if (dfsFindCycle(next, curr, visited)) return true;
                }
            }
            return false;
        }

        // --- BOOLEAN D&C REFEREE LOGIC ---
        private boolean dncAreCellsValid(List<Cell> cells, int left, int right) {
            if (left == right) {
                Cell c = cells.get(left);
                if (c.clue != -1 && (c.filledCount > c.clue || c.filledCount + c.unknownCount < c.clue)) return false;
                return true;
            }
            int mid = left + (right - left) / 2;
            return dncAreCellsValid(cells, left, mid) && dncAreCellsValid(cells, mid + 1, right);
        }

        private boolean dncAreNodesValid(List<Node> nodes, int left, int right) {
            if (left == right) {
                Node n = nodes.get(left);
                if (n.filledCount > 2 || (n.filledCount == 1 && n.unknownCount == 0)) return false;
                return true;
            }
            int mid = left + (right - left) / 2;
            return dncAreNodesValid(nodes, left, mid) && dncAreNodesValid(nodes, mid + 1, right);
        }

        private boolean dncAreCellsFinished(List<Cell> cells, int left, int right) {
            if (left == right) {
                Cell c = cells.get(left);
                if (c.clue != -1 && c.filledCount != c.clue) return false;
                return true;
            }
            int mid = left + (right - left) / 2;
            return dncAreCellsFinished(cells, left, mid) && dncAreCellsFinished(cells, mid + 1, right);
        }

        private boolean dncAreNodesFinished(List<Node> nodes, int left, int right) {
            if (left == right) {
                Node n = nodes.get(left);
                if (n.filledCount == 1) return false;
                return true;
            }
            int mid = left + (right - left) / 2;
            return dncAreNodesFinished(nodes, left, mid) && dncAreNodesFinished(nodes, mid + 1, right);
        }

        public String checkHumanMove(List<Cell> cells, List<Edge> edges, List<Node> nodes) {
            if (!dncAreCellsValid(cells, 0, cells.size() - 1)) return "Error: Move invalidates clues!";
            if (!dncAreNodesValid(nodes, 0, nodes.size() - 1)) return "Error: Branch or Dead End!";
            if (hasPrematureLoop(edges, nodes, cells)) return "Error: Secondary or Premature Loop!";
            
            boolean gameFinished = dncAreNodesFinished(nodes, 0, nodes.size() - 1) && dncAreCellsFinished(cells, 0, cells.size() - 1);
            if (gameFinished) {
                for (Edge e : edges) if (e.state == 1) return "VICTORY"; 
            }
            return "Valid";
        }
    }

    // ==========================================
    // 3. GUI & GAME LOOP
    // ==========================================

    private final int ROWS = 5; private final int COLS = 5;
    private final int GAP = 60; private final int OFFSET = 50;

    private static final BasicStroke LINE_STROKE = new BasicStroke(4);
    private static final BasicStroke CROSS_STROKE = new BasicStroke(2);
    private static final BasicStroke EMPTY_STROKE = new BasicStroke(1);
    private static final Color LINE_COLOR = new Color(0, 100, 255);
    private static final Color EMPTY_COLOR = new Color(220, 220, 220);

    private List<Node> nodes = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();
    private List<Cell> cells = new ArrayList<>();
    private List<int[][]> puzzles = new ArrayList<>();
    private Stack<int[]> history = new Stack<>();
    
    public boolean isProcessing = false;
    private ComputerPartner ai;
    public JLabel statusLabel;

    public CoopLoopy3() {
        this.setPreferredSize(new Dimension(COLS * GAP + 100, ROWS * GAP + 150));
        this.setBackground(Color.WHITE);
        this.ai = new ComputerPartner();

        puzzles.add(new int[][]{
            {3, -1, -1,  3, -1}, {-1, 1, -1, -1,  2}, {-1,-1, -1, -1, -1}, {2, -1,  0, -1,  1}, {-1, 3, -1,  3, -1}
        });

        puzzles.add(new int[][]{
            {3,  2, -1, -1, -1}, {2, -1,  1, -1, -1}, {3, -1,  0, -1,  2}, {2, -1,  1, -1, -1}, {3,  2, -1, -1, -1}
        });

        puzzles.add(new int[][]{
            {-1, 2, -1, 2, -1}, { 2,-1,  2,-1,  2}, {-1, 2, -1, 2, -1}, { 2,-1,  2,-1,  2}, {-1, 2, -1, 2, -1}
        });

        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!isProcessing) handleMouseClick(e.getX(), e.getY());
            }
        });
    }

    public void loadPuzzle(int index) { initializeGame(index); }

    private void initializeGame(int puzzleIndex) {
        nodes.clear(); edges.clear(); cells.clear(); history.clear();
        isProcessing = false;
        if (statusLabel != null) statusLabel.setText("Status: Puzzle " + (puzzleIndex + 1) + " Loaded.");

        int nodeId = 0;
        Node[][] nodeGrid = new Node[ROWS + 1][COLS + 1];
        
        for (int r = 0; r <= ROWS; r++) {
            for (int c = 0; c <= COLS; c++) {
                Node n = new Node(r, c, nodeId++);
                nodeGrid[r][c] = n; nodes.add(n);
            }
        }
        
        for (int r = 0; r <= ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Edge e = new Edge(nodeGrid[r][c], nodeGrid[r][c + 1], true);
                edges.add(e); nodeGrid[r][c].connectedEdges.add(e); nodeGrid[r][c + 1].connectedEdges.add(e);
            }
        }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c <= COLS; c++) {
                Edge e = new Edge(nodeGrid[r][c], nodeGrid[r + 1][c], false);
                edges.add(e); nodeGrid[r][c].connectedEdges.add(e); nodeGrid[r + 1][c].connectedEdges.add(e);
            }
        }
        
        int[][] puzzleData = puzzles.get(puzzleIndex);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Cell cell = new Cell(r, c, puzzleData[r][c]);
                Node tl = nodeGrid[r][c]; Node tr = nodeGrid[r][c + 1];
                Node bl = nodeGrid[r + 1][c]; Node br = nodeGrid[r + 1][c + 1];
                for (Edge e : edges) {
                    if ((e.n1==tl && e.n2==tr) || (e.n1==bl && e.n2==br) || (e.n1==tl && e.n2==bl) || (e.n1==tr && e.n2==br)) {
                        cell.edges.add(e); e.connectedCells.add(cell);
                    }
                }
                cells.add(cell);
            }
        }

        for (Node n : nodes) n.initCache();

        repaint();
    }

    private void saveState() {
        int[] snapshot = new int[edges.size()];
        for (int i = 0; i < edges.size(); i++) snapshot[i] = edges.get(i).state;
        history.push(snapshot);
    }

    public void undoLastMove() {
        if (history.isEmpty()) return;
        int[] snapshot = history.pop();
        for (int i = 0; i < edges.size(); i++) edges.get(i).setState(snapshot[i]);
        statusLabel.setText("Status: Move Undone.");
        repaint();
    }

    private void handleMouseClick(int x, int y) {
        boolean moveMade = false;
        Edge changedEdge = null;
        int oldState = 0;
        
        for (Edge e : edges) {
            if (e.contains(x, y, GAP, OFFSET, OFFSET)) {
                saveState();
                changedEdge = e; oldState = e.state;
                e.setState((e.state + 1) % 3); 
                moveMade = true; break;
            }
        }
        
        if (moveMade) {
            isProcessing = true;
            repaint();
            
            final Edge targetEdge = changedEdge;
            final int targetOldState = oldState;
            
            Timer humanTimer = new Timer(50, e -> {
                String result = ai.checkHumanMove(cells, edges, nodes);
                if (result.equals("VICTORY")) {
                    statusLabel.setText("Status: YOU WIN! PUZZLE SOLVED!");
                    JOptionPane.showMessageDialog(this, "CONGRATULATIONS!", "VICTORY", JOptionPane.INFORMATION_MESSAGE);
                    isProcessing = false;
                } else if (result.startsWith("Error")) {
                    targetEdge.setState(targetOldState);
                    if (!history.isEmpty()) history.pop();
                    statusLabel.setText("Referee: " + result);
                    repaint(); isProcessing = false;
                } else {
                    statusLabel.setText("AI Thinking..."); repaint();
                    Timer aiTimer = new Timer(50, e2 -> {
                        String aiResult = ai.makeMove(cells, nodes, edges);
                        statusLabel.setText(aiResult);
                        repaint(); isProcessing = false;
                        
                        if (ai.checkHumanMove(cells, edges, nodes).equals("VICTORY")) {
                            statusLabel.setText("Status: AI FINISHED THE BOARD! YOU WIN!");
                        }
                    });
                    aiTimer.setRepeats(false); aiTimer.start();
                }
            });
            humanTimer.setRepeats(false); humanTimer.start();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(Color.BLACK);
        for (Node n : nodes) g2.fillOval(n.c * GAP + OFFSET - 3, n.r * GAP + OFFSET - 3, 6, 6);

        g2.setFont(new Font("Arial", Font.BOLD, 20));
        for (Cell c : cells) if (c.clue != -1) g2.drawString(String.valueOf(c.clue), c.c * GAP + OFFSET + GAP / 2 - 5, c.r * GAP + OFFSET + GAP / 2 + 5);

        for (Edge e : edges) {
            int x1 = e.n1.c * GAP + OFFSET; int y1 = e.n1.r * GAP + OFFSET;
            int x2 = e.n2.c * GAP + OFFSET; int y2 = e.n2.r * GAP + OFFSET;
            if (e.state == 1) { 
                g2.setColor(LINE_COLOR); g2.setStroke(LINE_STROKE); g2.drawLine(x1, y1, x2, y2);
            } else if (e.state == 2) { 
                g2.setColor(Color.RED); g2.setStroke(CROSS_STROKE);
                int cx = (x1 + x2) / 2; int cy = (y1 + y2) / 2;
                g2.drawLine(cx - 5, cy - 5, cx + 5, cy + 5); g2.drawLine(cx - 5, cy + 5, cx + 5, cy - 5);
            } else { 
                g2.setColor(EMPTY_COLOR); g2.setStroke(EMPTY_STROKE); g2.drawLine(x1, y1, x2, y2);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("trial 4 - Pure Greedy");
            CoopLoopy3 gamePanel = new CoopLoopy3();
            gamePanel.statusLabel = new JLabel("Status: Select a Puzzle");
            gamePanel.statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            CardLayout cardLayout = new CardLayout();
            JPanel mainContainer = new JPanel(cardLayout);

            JPanel menuPanel = new JPanel();
            menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));
            menuPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

            JLabel title = new JLabel("Co-op Loopy");
            title.setFont(new Font("Arial", Font.BOLD, 24)); title.setAlignmentX(Component.CENTER_ALIGNMENT);
            
            JTextArea rules = new JTextArea("Click below to load a level.");
            rules.setEditable(false); rules.setBackground(menuPanel.getBackground());
            rules.setFont(new Font("Arial", Font.PLAIN, 14)); rules.setAlignmentX(Component.CENTER_ALIGNMENT);

            JButton btn1 = new JButton("Example 1");
            JButton btn2 = new JButton("Example 2");
            JButton btn3 = new JButton("Example 3");

            btn1.setAlignmentX(Component.CENTER_ALIGNMENT); btn2.setAlignmentX(Component.CENTER_ALIGNMENT); btn3.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn1.addActionListener(e -> { gamePanel.loadPuzzle(0); cardLayout.show(mainContainer, "GAME"); });
            btn2.addActionListener(e -> { gamePanel.loadPuzzle(1); cardLayout.show(mainContainer, "GAME"); });
            btn3.addActionListener(e -> { gamePanel.loadPuzzle(2); cardLayout.show(mainContainer, "GAME"); });

            menuPanel.add(title); menuPanel.add(Box.createRigidArea(new Dimension(0, 20)));
            menuPanel.add(rules); menuPanel.add(Box.createRigidArea(new Dimension(0, 20)));
            menuPanel.add(btn1); menuPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            menuPanel.add(btn2); menuPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            menuPanel.add(btn3);

            JPanel gameContainer = new JPanel(new BorderLayout());
            JButton backButton = new JButton("Back to Menu");
            JButton undoButton = new JButton("Undo");

            backButton.addActionListener(e -> cardLayout.show(mainContainer, "MENU"));
            undoButton.addActionListener(e -> { if (!gamePanel.isProcessing) gamePanel.undoLastMove(); });

            JPanel bottomPanel = new JPanel(new BorderLayout());
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            buttonPanel.add(backButton); buttonPanel.add(undoButton);
            bottomPanel.add(buttonPanel, BorderLayout.WEST);
            bottomPanel.add(gamePanel.statusLabel, BorderLayout.CENTER);

            gameContainer.add(gamePanel, BorderLayout.CENTER);
            gameContainer.add(bottomPanel, BorderLayout.SOUTH);

            mainContainer.add(menuPanel, "MENU"); mainContainer.add(gameContainer, "GAME");
            frame.add(mainContainer); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack(); frame.setLocationRelativeTo(null); frame.setVisible(true);
        });
    }
}
