import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.Stack;
import javax.swing.Timer;

// ==========================================
// 1. DATA STRUCTURES (Member 1)
// ==========================================

class Node {
    int r, c, id;
    List<Edge> connectedEdges = new ArrayList<>();
    public Node(int r, int c, int id) { this.r = r; this.c = c; this.id = id; }
    
    public int getFilledDegree() {
        int count = 0;
        for (Edge e : connectedEdges) if (e.state == 1) count++;
        return count;
    }
}

class Edge {
    Node n1, n2;
    boolean isHorizontal;
    int state; // 0 = Empty, 1 = Line, 2 = Cross

    public Edge(Node n1, Node n2, boolean isHorizontal) {
        this.n1 = n1; this.n2 = n2; this.isHorizontal = isHorizontal; this.state = 0;
    }

    public boolean contains(int x, int y, int gap, int offsetX, int offsetY) {
        int x1 = n1.c * gap + offsetX;
        int y1 = n1.r * gap + offsetY;
        if (isHorizontal) return x >= x1 && x <= x1 + gap && y >= y1 - 10 && y <= y1 + 10;
        else return x >= x1 - 10 && x <= x1 + 10 && y >= y1 && y <= y1 + gap;
    }
}

class Cell implements Comparable<Cell> {
    int r, c;
    int clue; 
    List<Edge> edges = new ArrayList<>();
    public Cell(int r, int c, int clue) { this.r = r; this.c = c; this.clue = clue; }
    
    public int getFilledCount() {
        int c = 0; for (Edge e : edges) if (e.state == 1) c++; return c;
    }
    public int getEmptyCount() {
        int c = 0; for (Edge e : edges) if (e.state == 0) c++; return c;
    }
    @Override
    public int compareTo(Cell other) { return Integer.compare(other.clue, this.clue); }
}

// ==========================================
// 2. LOGIC & RULES (Member 2 & 3)
// ==========================================

class UnionFind {
    int[] parent;
    public UnionFind(int size) {
        parent = new int[size];
        for (int i = 0; i < size; i++) parent[i] = i;
    }
    int find(int i) {
        if (parent[i] == i) return i;
        return parent[i] = find(parent[i]);
    }
    void union(int i, int j) {
        int rootI = find(i);
        int rootJ = find(j);
        if (rootI != rootJ) parent[rootI] = rootJ;
    }
}

class ComputerPartner {
    
    // --- VALIDATION (Member 2) ---
    public String checkMove(List<Cell> sortedCells, List<Edge> allEdges, List<Node> nodes) {
        for (Cell c : sortedCells) {
            if (c.clue == -1) continue;
            int filled = c.getFilledCount();
            int empty = c.getEmptyCount();
            if (filled > c.clue) return "Error: Cell " + c.clue + " overloaded!";
            if (filled + empty < c.clue) return "Error: Cell " + c.clue + " broken!";
        }
        for (Node n : nodes) {
            int filled = n.getFilledDegree();
            int empty = 0;
            for(Edge e : n.connectedEdges) if(e.state == 0) empty++;
            if (filled > 2) return "Error: Branch detected!";
            if (filled == 1 && empty == 0) return "Error: Dead End detected!";
        }
        
        UnionFind uf = new UnionFind(nodes.size());
        boolean cycleDetected = false;
        int filledEdgeCount = 0;
        for (Edge e : allEdges) {
            if (e.state == 1) {
                filledEdgeCount++;
                int u = e.n1.id; int v = e.n2.id;
                if (uf.find(u) == uf.find(v)) cycleDetected = true;
                uf.union(u, v);
            }
        }
        if (cycleDetected) {
            if (!areCluesSatisfied(sortedCells)) return "Error: Clues unsatisfied!";
            if (!isGraphConnected(allEdges, filledEdgeCount)) return "Error: Disconnected loop!";
            return "VICTORY";
        }
        return "Valid";
    }

    private boolean areCluesSatisfied(List<Cell> cells) {
        for (Cell c : cells) if (c.clue != -1 && c.getFilledCount() != c.clue) return false;
        return true;
    }

    private boolean isGraphConnected(List<Edge> allEdges, int totalFilled) {
        Node startNode = null;
        for (Edge e : allEdges) { if (e.state == 1) { startNode = e.n1; break; } }
        if (startNode == null) return false; 
        Set<Edge> visitedEdges = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        Set<Integer> visitedNodes = new HashSet<>();
        queue.add(startNode); visitedNodes.add(startNode.id);
        while (!queue.isEmpty()) {
            Node curr = queue.poll();
            for (Edge e : curr.connectedEdges) {
                if (e.state == 1 && !visitedEdges.contains(e)) {
                    visitedEdges.add(e);
                    Node neighbor = (e.n1 == curr) ? e.n2 : e.n1;
                    if (!visitedNodes.contains(neighbor.id)) {
                        visitedNodes.add(neighbor.id); queue.add(neighbor);
                    }
                }
            }
        }
        return visitedEdges.size() == totalFilled;
    }

    // --- AI HEURISTICS (Member 3) ---
    public String makeMove(List<Cell> cells, List<Node> nodes, List<Edge> allEdges, int ROWS, int COLS) {
        
        for (Cell c : cells) {
            if (c.clue == -1) continue;
            int needed = c.clue - c.getFilledCount();
            if (needed > 0 && needed == c.getEmptyCount()) {
                for (Edge e : c.edges) {
                    if (e.state == 0) {
                        e.state = 1; 
                        if (checkMove(cells, allEdges, nodes).startsWith("Error")) e.state = 0; 
                        else return "Computer: Filled forced lines around " + c.clue;
                    }
                }
            }
        }

        for (Node n : nodes) {
            int filled = n.getFilledDegree();
            int empty = 0;
            List<Edge> empties = new ArrayList<>();
            for(Edge e : n.connectedEdges) if(e.state == 0) { empty++; empties.add(e); }
            
            if (filled == 2 && empty > 0) {
                for(Edge e : empties) e.state = 2;
                return "Computer: Path continues (no branching).";
            }
            if (filled == 1 && empty == 1) {
                empties.get(0).state = 1; 
                if (checkMove(cells, allEdges, nodes).startsWith("Error")) empties.get(0).state = 0; 
                else return "Computer: Extended line (Forced Path).";
            }
        }

        for (Cell c : cells) {
            if (c.clue == 3) {
                 if (c.r == 0 && c.c == 0) if (tryFillOuter(c, true, true, cells, allEdges, nodes)) return "Computer: Corner 3";
                 if (c.r == 0 && c.c == COLS - 1) if (tryFillOuter(c, true, false, cells, allEdges, nodes)) return "Computer: Corner 3";
                 if (c.r == ROWS - 1 && c.c == 0) if (tryFillOuter(c, false, true, cells, allEdges, nodes)) return "Computer: Corner 3";
                 if (c.r == ROWS - 1 && c.c == COLS - 1) if (tryFillOuter(c, false, false, cells, allEdges, nodes)) return "Computer: Corner 3";
            }
        }
        
        for (Cell c : cells) {
            if (c.clue != -1 && c.getFilledCount() == c.clue && c.getEmptyCount() > 0) {
                for (Edge e : c.edges) if (e.state == 0) e.state = 2; 
                return "Computer: Completed " + c.clue + " (Crossed rest)";
            }
        }
        
        return "Computer: No obvious moves found.";
    }
    
    private boolean tryFillOuter(Cell c, boolean isTop, boolean isLeft, List<Cell> cells, List<Edge> allEdges, List<Node> nodes) {
        boolean success = false;
        for (Edge e : c.edges) {
            if (e.state == 0) {
                boolean target = false;
                if (isTop && e.isHorizontal && e.n1.r == c.r) target = true;
                if (!isTop && e.isHorizontal && e.n1.r == c.r + 1) target = true;
                if (isLeft && !e.isHorizontal && e.n1.c == c.c) target = true;
                if (!isLeft && !e.isHorizontal && e.n1.c == c.c + 1) target = true;
                if (target) {
                    e.state = 1;
                    if (checkMove(cells, allEdges, nodes).startsWith("Error")) e.state = 0; 
                    else success = true;
                }
            }
        }
        return success;
    }
}

// ==========================================
// 3. GUI & GAME LOOP (Member 4)
// ==========================================

public class CoopLoopy extends JPanel {
    private final int ROWS = 5; private final int COLS = 5;
    private final int GAP = 60; private final int OFFSET = 50;
    
    private List<Node> nodes = new ArrayList<>();
    private List<Edge> edges = new ArrayList<>();
    private List<Cell> cells = new ArrayList<>();
    private List<int[][]> puzzles = new ArrayList<>();
    private Stack<int[]> history = new Stack<>();
    public boolean isProcessing = false; 
    
    private ComputerPartner ai;
    public JLabel statusLabel;
    
    private static CardLayout cardLayout = new CardLayout();
    private static JPanel mainContainer = new JPanel(cardLayout);

    public CoopLoopy() {
        this.setPreferredSize(new Dimension(ROWS * GAP + 100, COLS * GAP + 150));
        this.setBackground(Color.WHITE);
        this.ai = new ComputerPartner();
        
        // --- Example 1: Standard ---
        puzzles.add(new int[][]{
            {3, -1, -1, 3, -1}, {-1, 1, -1, -1, 2}, {-1, -1, -1, -1, -1}, {2, -1, 0, -1, 1}, {-1, 3, -1, 3, -1}
        });
        
        // --- Example 2: Diagonal ---
        puzzles.add(new int[][]{
            {3, 2, -1, -1, -1}, {2, -1, 1, -1, -1}, {3, -1, 0, -1, 2}, {2, -1, 1, -1, -1}, {3, 2, -1, -1, -1}
        });
        
        // --- Example 3: Stripes (The Limit) ---
        puzzles.add(new int[][]{
            {-1, 2, -1, 2, -1}, {2, -1, 2, -1, 2}, {-1, 2, -1, 2, -1}, {2, -1, 2, -1, 2}, {-1, 2, -1, 2, -1}
        });

        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if(!isProcessing) handleMouseClick(e.getX(), e.getY(), e.getButton());
            }
        });
    }

    public void loadPuzzle(int index) {
        initializeGame(index);
    }

    private void initializeGame(int puzzleIndex) {
        nodes.clear(); edges.clear(); cells.clear(); history.clear(); 
        isProcessing = false;
        statusLabel.setText("Status: Puzzle " + (puzzleIndex+1) + " Loaded.");
        
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
                Edge e = new Edge(nodeGrid[r][c], nodeGrid[r][c+1], true);
                edges.add(e); nodeGrid[r][c].connectedEdges.add(e); nodeGrid[r][c+1].connectedEdges.add(e);
            }
        }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c <= COLS; c++) {
                Edge e = new Edge(nodeGrid[r][c], nodeGrid[r+1][c], false);
                edges.add(e); nodeGrid[r][c].connectedEdges.add(e); nodeGrid[r+1][c].connectedEdges.add(e);
            }
        }
        int[][] puzzleData = puzzles.get(puzzleIndex);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Cell cell = new Cell(r, c, puzzleData[r][c]);
                linkEdgesToCell(cell, nodeGrid); cells.add(cell);
            }
        }
        Collections.sort(cells); repaint();
    }
    
    private void linkEdgesToCell(Cell cell, Node[][] nodeGrid) {
        Node tl = nodeGrid[cell.r][cell.c]; Node tr = nodeGrid[cell.r][cell.c+1];
        Node bl = nodeGrid[cell.r+1][cell.c]; Node br = nodeGrid[cell.r+1][cell.c+1];
        for (Edge e : edges) {
            if ((e.n1 == tl && e.n2 == tr) || (e.n1 == bl && e.n2 == br) || (e.n1 == tl && e.n2 == bl) || (e.n1 == tr && e.n2 == br)) {
                cell.edges.add(e);
            }
        }
    }
    
    private void saveState() {
        int[] snapshot = new int[edges.size()];
        for (int i = 0; i < edges.size(); i++) snapshot[i] = edges.get(i).state;
        history.push(snapshot);
    }
    
    public void undoLastMove() {
        if (history.isEmpty()) return;
        int[] snapshot = history.pop();
        for (int i = 0; i < edges.size(); i++) edges.get(i).state = snapshot[i];
        statusLabel.setText("Status: Move Undone.");
        repaint();
    }

    private void handleMouseClick(int x, int y, int button) {
        boolean moveMade = false;
        Edge changedEdge = null; int oldState = 0;
        for (Edge e : edges) {
            if (e.contains(x, y, GAP, OFFSET, OFFSET)) {
                saveState(); changedEdge = e; oldState = e.state;
                e.state = (e.state + 1) % 3; moveMade = true; break;
            }
        }
        if (moveMade) {
            isProcessing = true; repaint(); 
            final Edge targetEdge = changedEdge; final int targetOldState = oldState;
            Timer humanTimer = new Timer(50, e -> {
                String result = ai.checkMove(cells, edges, nodes);
                if (result.equals("VICTORY")) {
                    statusLabel.setText("Status: YOU WIN! PUZZLE SOLVED!");
                    JOptionPane.showMessageDialog(this, "CONGRATULATIONS!", "VICTORY", JOptionPane.INFORMATION_MESSAGE);
                    isProcessing = false;
                } else if (result.startsWith("Error")) {
                    targetEdge.state = targetOldState; if(!history.isEmpty()) history.pop();
                    statusLabel.setText("Referee: " + result); repaint(); isProcessing = false;
                } else {
                    statusLabel.setText("AI Thinking..."); repaint();
                    Timer aiTimer = new Timer(50, e2 -> {
                        String aiResult = ai.makeMove(cells, nodes, edges, ROWS, COLS);
                        statusLabel.setText(aiResult); repaint(); isProcessing = false;
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
        for (Cell c : cells) if (c.clue != -1) g2.drawString(String.valueOf(c.clue), c.c * GAP + OFFSET + GAP/2 - 5, c.r * GAP + OFFSET + GAP/2 + 5);
        for (Edge e : edges) {
            int x1 = e.n1.c * GAP + OFFSET; int y1 = e.n1.r * GAP + OFFSET;
            int x2 = e.n2.c * GAP + OFFSET; int y2 = e.n2.r * GAP + OFFSET;
            if (e.state == 1) { 
                g2.setColor(new Color(0, 100, 255)); g2.setStroke(new BasicStroke(4)); g2.drawLine(x1, y1, x2, y2);
            } else if (e.state == 2) { 
                g2.setColor(Color.RED); g2.setStroke(new BasicStroke(1));
                int cx = (x1 + x2) / 2, cy = (y1 + y2) / 2;
                g2.drawLine(cx - 5, cy - 5, cx + 5, cy + 5); g2.drawLine(cx - 5, cy + 5, cx + 5, cy - 5);
            } else { 
                g2.setColor(new Color(220, 220, 220)); g2.setStroke(new BasicStroke(1)); g2.drawLine(x1, y1, x2, y2);
            }
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Co-op Loopy");
        CoopLoopy gamePanel = new CoopLoopy();
        gamePanel.statusLabel = new JLabel("Status: Select a Puzzle");
        gamePanel.statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // --- MENU PANEL ---
        JPanel menuPanel = new JPanel();
        menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));
        menuPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel title = new JLabel("Co-op Loopy: Guidelines");
        title.setFont(new Font("Arial", Font.BOLD, 24));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JTextArea rules = new JTextArea(
            "RULES:\n" +
            "1. Connect dots vertically or horizontally.\n" +
            "2. Numbers (0-3) show lines around a cell.\n" +
            "3. Loop must be ONE single continuous path.\n" +
            "4. No branching (3 lines at a dot is illegal).\n\n" +
            "Click a level below to start:"
        );
        rules.setEditable(false);
        rules.setBackground(menuPanel.getBackground());
        rules.setFont(new Font("Arial", Font.PLAIN, 14));
        rules.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // --- BUTTONS ---
        JButton btn1 = new JButton("Example 1");
        JButton btn2 = new JButton("Example 2");
        JButton btn3 = new JButton("Example 3");
        
        btn1.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn2.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn3.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        btn1.addActionListener(e -> { gamePanel.loadPuzzle(0); cardLayout.show(mainContainer, "GAME"); });
        btn2.addActionListener(e -> { gamePanel.loadPuzzle(1); cardLayout.show(mainContainer, "GAME"); });
        btn3.addActionListener(e -> { gamePanel.loadPuzzle(2); cardLayout.show(mainContainer, "GAME"); });

        menuPanel.add(title);
        menuPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        menuPanel.add(rules);
        menuPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        menuPanel.add(btn1);
        menuPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        menuPanel.add(btn2);
        menuPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        menuPanel.add(btn3);
        
        // --- GAME PANEL ---
        JPanel gameContainer = new JPanel(new BorderLayout());
        JButton backButton = new JButton("Back to Menu");
        JButton undoButton = new JButton("Undo");
        
        backButton.addActionListener(e -> cardLayout.show(mainContainer, "MENU"));
        undoButton.addActionListener(e -> { if(!gamePanel.isProcessing) gamePanel.undoLastMove(); });
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(backButton);
        buttonPanel.add(undoButton);
        bottomPanel.add(buttonPanel, BorderLayout.WEST); 
        bottomPanel.add(gamePanel.statusLabel, BorderLayout.CENTER);
        
        gameContainer.add(gamePanel, BorderLayout.CENTER);
        gameContainer.add(bottomPanel, BorderLayout.SOUTH);

        mainContainer.add(menuPanel, "MENU");
        mainContainer.add(gameContainer, "GAME");
        
        frame.add(mainContainer);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}