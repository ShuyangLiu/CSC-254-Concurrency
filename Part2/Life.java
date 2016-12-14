/*
    Life.java

    Graphical implementation of Conway's game of Life.

    Currently single-threaded, but has infrastructure for multithreaded
    solutions.

    Michael L. Scott, November 2016, based on earlier versions from
    1998, 2007, and 2011.

    Edited by Shuyang Liu and Nina Bose in 2016
 */

import java.awt.*;          // older of the two standard Java GUIs
import java.awt.event.*;
import javax.swing.*;
import java.lang.Thread.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.io.*;

public class Life {
    private static final int n = 100;    // number of cells on a side
    private static int pauseIterations = -(500000000/n/n);
        // nanoseconds per dot for a delay of about a half a second
    private static int numThreads = 1;
    private static int numTasks = 10; 		// default number of tasks is 10.
    private static boolean headless = false;    // don't create GUI
    private static boolean glider = false;      // create initial glider
    private static List<Point> shape = null;	// used to represent a custom shape specified by the user in a config file.


    // Helper method to create the UI. 
    private UI buildUI(RootPaneContainer pane, int numTasks, List<Point> shape) {
        return new UI(n, pane, pauseIterations, headless, glider, numThreads, numTasks, shape);
    }

    // Print error message and exit.
    //
    private static void die(String msg) {
        System.err.print(msg);
        System.exit(-1);
    }

    // Examine command-line arguments for non-default parameters.
    //
    private static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
  	   // Fill in information from config file. Values in this file are overridden 
	   // by parameters specified in the command line. 
           if (args[i].equals("-c")) {
		if (++i >= args.length) {
		   die("Missing config file.\n");
		} else {
		   Parser p = new Parser();
		   Configuration config = p.parse(args[i]);
 		   if (config.isPresent()) {
		       // Check to see whether the value in question has been specified in 
		       // the config file and has not been specified in command line arguments.
		       // If so, then store the value. 
			if (config.numThreads != -1 && numThreads == 1) {
				numThreads = config.numThreads;
			} 
			if (config.spin != -1 && pauseIterations == -(500000000/n/n)) {
				pauseIterations = config.spin;
			}
			if (config.shape != null && !glider) {
				shape = config.shape;
			}
		   } else { System.err.println("Could not configure from file. Using default values instead."); }
		}
	    } else if (args[i].equals("-t")) {
                if (++i >= args.length) {
                    die("Missing number of threads\n");
                } else {
                    int nt = -1;
                    try {
                        nt = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) { }
                    if (nt > 0) {
                        numThreads = nt;
                    } else {
                        die(String.format("Invalid number of threads: %s\n",
                                          args[i]));
                    }
                }
            } else if (args[i].equals("-s")) {
                if (++i >= args.length) {
                    die("Missing number of spin iterations\n");
                } else {
                    int di = -1;
                    try {
                        di = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) { }
                    if (di > 0) {
                        pauseIterations = di;
                    } else {
                        die(String.format("Invalid number of spin iterations: %s\n",
                                          args[i]));
                    }
                }
            } else if (args[i].equals("-k")) { // The user may specify the number of tasks to use in the range (0, 100].
                if (++i >= args.length) {
                    die("Missing number of tasks\n");
                } else {
                    int k = -1;
                    try {
                        k = Integer.parseInt(args[i]);
                    } catch (NumberFormatException e) { }
                    if (k > 0 && k <= 100) {
                        numTasks = k;
                    } else {
                        die(String.format("Invalid number of tasks: %s\n",
                                          args[i]));
                    }
                }
            } else if (args[i].equals("--headless")) {
                headless = true;
            } else if (args[i].equals("--glider")) {
                glider = true;
            } else {
                die(String.format("Unexpected argument: %s\n", args[i]));
            }
        }
    }

    public static void main(String[] args) {
        parseArgs(args);
        Life me = new Life();
        JFrame f = new JFrame("Life");
        f.addWindowListener(new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            System.exit(0);
          }
        });
        UI ui = me.buildUI(f, numTasks, shape);
        if (headless) {
            ui.onRunClick();
        } else {
          f.pack();
          f.setVisible(true);
        }
    }
}

// This class is designed to parse a configuration file.
class Parser {

   // Method to extract information from a configuration file and store it in a
   // Configuration object.
   public Configuration parse(String fileName) {
	Configuration config = new Configuration();
	try(BufferedReader br = new BufferedReader(new FileReader(fileName))) {
		String line = "";
		while( (line = br.readLine()) != null) {
			line = line.trim().replace(" ", "");
			if( line.startsWith("t:") ) {
				String threads = line.replace("t:", "");
				try {
					int numThreads = Integer.parseInt(threads);
					if (numThreads > 0) {
						config.numThreads = numThreads;
					} else { System.err.println("Whoops! Threads in config file must be > 0."); }
					
				} catch (NumberFormatException e) { System.err.println("Cannot read number of threads. Is the format \"t: <number here>\"?");}
			} else if( line.startsWith("s:") ) {
				String s = line.replace("s:", "");
				try {
					Integer spin = Integer.parseInt(s);
					if (spin > 0) {
						config.spin = spin;
					} else { System.err.println("Whoops! Spin in config file must be > 0."); }
					
				} catch (NumberFormatException e) { System.err.println("Cannot read spin. Is the format \"s: <number here>\"?");}

			} else if( line.startsWith("shape:") ){
				String s = line.replace("shape:", "");
				List<Point> shape = getPoints(s);
				config.shape = shape;

			}
		}	
	} catch (IOException e) { System.err.println("Cannot open file. Reverting to default configurations.");}
	return config;
   }  

    // A shape can be represented as a List of points on the UI. This method parses that information
    // from a string written in the following format: (x1,y1);(x2,y2);(x3,y3);(x4,y4) 	
    private List<Point> getPoints(String s) {
	String[] coordinates = s.split(";");
	List<Point> points = new ArrayList<>();
	for(String coor : coordinates) {
		coor = coor.replace("(","").replace(")", "");
		String[] point = coor.split(",");
		try {
			int x = Integer.parseInt(point[0]);
			int y = Integer.parseInt(point[1]);
			if (x > 0 && y > 0 && x < 100 && y < 100) { // NOTE: HARDCODED BOUNDRIES
				points.add(new Point(x, y));
			} else { System.err.println("Whoops! Coordinates must be in the bounds of the board in the config file.");}
			
		} catch (NumberFormatException e) { System.err.println("Cannot read points. Are they numbers?");}
	}
	return points;
	
    }

}

// Wrapper class to store information that could be in config file. 
// It would have been better coding style to write it using the
// Builder design pattern and/or using Optionals, but due to the lack
// of time, we have implemented it as a general Java class.
class Configuration {
    public int numThreads;
    public int spin;
    public List<Point> shape;	

    public Configuration() {
	numThreads = -1;
	spin = -1;
	shape = null;
    }

    public Configuration(int NT, int S, List<Point> SH) {
	numThreads = NT;
	spin = S;
	shape = SH;
    }

    public boolean isPresent() {
        if (numThreads == -1 && spin == -1L && shape == null) {
		return false;
	}
	return true;
    }
}

// Represents an x,y coordinate. 
class Point {
    int x;
    int y;

    public Point(int row, int col) {
        x = row;
        y = col;
    }

    public String toString() {
	return "(" + String.valueOf(x) + "," + String.valueOf(y) + ")";
    }

}

// Controller class where threads are created using ExecutorService and the 
// appropraite tasks are delegated and run by the threads.
class Delegator implements Runnable {
    private final LifeBoard lb;
    private final Coordinator c;
    private final UI u;
    private final int nt;
    private final int k;
    private final ExecutorService pool;

    public Delegator(LifeBoard LB, Coordinator C, UI U, int numThreads, int numTasks) {
        lb = LB;
        c = C;
        u = U;
        nt = numThreads;
        k = numTasks;
	// Creating a pool of threads.
        pool = Executors.newFixedThreadPool(nt);
    }

    public void run() {
      try {
        c.register();
          while(true) {
              runOneGeneration();
	      // The following statement helps in the functionality of 
	      // the Step Button, which allows a user to go through the
	      // game generation by generation.
	      if (u.step_switch) {
		  u.pauseButton.doClick();
              }
	      u.step_switch = false;
          }
      } catch (Coordinator.KilledException e) {}
      finally {
        c.unregister();
      }

    }

    // Creates a sets of tasks and assigns them to the pool of threads using invokeAll.
    public void runOneGeneration() throws Coordinator.KilledException {
        List<Callable<Boolean>> tasks = generateTasks(k);
        try {
	  // invokeAll takes care of the syncronization and task delegation. 
	  // Given a set of tasks, it delegates them appropriately to each thread.
	  // It returns when all tasks have been completed. Thus, as the programmer, 
	  // we do not have to worry about creating a barrier. 
          pool.invokeAll(tasks);
          lb.updateBoard();
        } catch (InterruptedException e) { System.err.println("Exception :("); }
    }

    // Divides the board into ranges of contiguous rows to be updated by a thread.
    // Each task should have roughly n/numTask rows.
    public List<Callable<Boolean>> generateTasks(int numTasks) {
      double begin = 0;
      double interval = (lb.n*1.0) /( numTasks*1.0);
      double end = interval;
      List<Callable<Boolean>> tasks = new ArrayList<>();
      for(int i = 0; i < numTasks; i++) {
	  if(end >= lb.n-1) {
		end = lb.n*1.0;
	  }
          tasks.add(new Worker(lb, c, u, new Task((int) begin, (int) end)));
          begin += interval;
          end += interval;
      }
      return tasks;
    }
}

// The Worker is the thread that does the actual work of calculating new
// generations.
//
// We made this a Callable as invokeAll takes a collection of Callables.
class Worker implements Callable<Boolean> {
    private final LifeBoard lb;
    private final Coordinator c;
    private final UI u;
    private final Task t;

    // The run() method of a Java Thread is never invoked directly by
    // user code.  Rather, it is called by the Java runtime when user
    // code calls start().
    //
    // This method updates the appropriate number of rows and returns a boolean.
    // The boolean returned is of little use. It provides of with the convenience of
    // using Callables, which seemed to be allowed in the prompt, as stated here:
    // "In the Executor framework, you can use built-in Executor methods to force all extant tasks 
    // to complete before starting the next generation."  
    public Boolean call() {
        try {
            c.register();
            try {
                    lb.doGeneration(t);
            } catch(Coordinator.KilledException e) { return false; /* throw exception instead of catching it? */}
        } finally {
            c.unregister();
        }
        return true;
    }

    // Constructor
    //
    public Worker(LifeBoard LB, Coordinator C, UI U, Task task) {
        lb = LB;
        c = C;
        u = U;
        t = task;
    }
}

// Represents the range of rows that a thread should update.
// start is inclusive and end is exclusive. In other words,
// the range looks like, [start, end).
class Task {
    int start; // First row to be updated (inclusive).
    int end; // First row after last row that should be update. 

    public Task(int s, int e) {
      start = s;
      end = e;
    }

}

// The LifeBoard is the Life world, containing all the cells.
// It embeds all knowledge about how to display things graphically.
//
class LifeBoard extends JPanel {
    private static final int width = 800;      // canvas dimensions
    private static final int height = 800;
    private static final int dotsize = 6;
    private static final int border = dotsize;
    static  boolean headless = false;
    private int B[][];  // board contents
    private int A[][];  // scratch board
    private int T[][];  // temporary pointer
    private int generation = 0;

    private static long start_time;

    // following fields are set by constructor:
    private final Coordinator c;
    private final UI u;
    public final int n;  // number of cells on a side.  

    // Called by the UI when it wants to start over.
    //
    public void clear() {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                B[i][j] = 0;
            }
        }
        repaint();
            // tell graphic system that LifeBoard needs to be re-rendered
    }

    // This is the function that actually plays (one full generation of)
    // the game.  It is called by the run() method of Thread class
    // Worker.    
    //
    // We split the original method into two separate methods, doGeneration and updateBoard.
    // Instead of updating the entire board at once, each thread updates some number of rows. 

    public void doGeneration(Task task) throws Coordinator.KilledException {
        for (int i = task.start; i < task.end; i++) {
            for (int j = 0; j < n; j++) {

                // NOTICE: you are REQUIRED to call hesitate() EVERY TIME
                // you update a LifeBoard cell.  The call serves two
                // purposes: (1) it checks to see whether you should pause
                // or stop; (2) it introduces delay that allows you to
                // see the board evolving and that will give you the
                // appearance of speedup with additional threads.

                c.hesitate();
                int im = (i+n-1) % n; int ip = (i+1) % n;
                int jm = (j+n-1) % n; int jp = (j+1) % n;
                switch (B[im][jm] + B[im][j] + B[im][jp] +
                        B[i][jm]             + B[i][jp] +
                        B[ip][jm] + B[ip][j] + B[ip][jp]) {
                    case 0 :
                    case 1 : A[i][j] = 0;       break;
                    case 2 : A[i][j] = B[i][j]; break;
                    case 3 : A[i][j] = 1;       break;
                    case 4 :
                    case 5 :
                    case 6 :
                    case 7 :
                    case 8 : A[i][j] = 0;       break;
                }
            }
        }

    }


    // This method updates and repaints the board (if necessary) when called. 
    // It is called when all of the threads have finished updating their rows.
    public void updateBoard() throws Coordinator.KilledException {
      c.hesitate();
      T = B;  B = A;  A = T;
      if (headless) {
          if (generation % 10 == 0) {
              System.out.print(System.currentTimeMillis() + ", ");
          }
      } else {
          repaint ();
      }
      ++generation;
    }

    // The following method is called automatically by the graphics
    // system when it thinks the LifeBoard canvas needs to be
    // re-displayed.  This can happen because code elsewhere in this
    // program called repaint(), or because of hiding/revealing or
    // open/close operations in the surrounding window system.
    //
    public void paintComponent(Graphics g) {
      if (headless) {
        return;
      }
        final Graphics2D g2 = (Graphics2D) g;

        super.paintComponent(g);    // clears panel

        // The following is synchronized to avoid race conditions with
        // worker threads.
        synchronized (u) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    drawSpot (i, j, g);
                }
            }
        }
    }

    public void toggleClick (int mx, int my) {
        Dimension d = (getSize ());
        int x = n * mx / d.width;
        int y = n * my / d.height;
        Graphics g = getGraphics ();
        mx = d.width * x / n;       // round to nearest spot center
        my = d.height * y / n;      // round to nearest spot center
        B[x][y] = 1 - B[x][y];
        drawSpot (x, y, g);
        g.dispose ();   // reclaim resources eagerly
    }

    private void drawSpot (int x, int y, Graphics g) {
        Dimension d = (getSize());
        int mx = d.width * x / n;       // round to nearest spot center
        int my = d.height * y / n;      // round to nearest spot center
        if (B[x][y] == 1) {
            g.setColor(Color.blue);
        } else {
            g.setColor(getBackground ());
        }
        g.fillOval (mx, my, dotsize, dotsize);
    }

    // Constructor
    //
    public LifeBoard(int N, Coordinator C, UI U,
                     boolean hdless, boolean glider, List<Point> shape) {
        n = N;
        c = C;
        u = U;
        headless = hdless;

        A = new int[n][n];  // initialized to all 0
        B = new int[n][n];  // initialized to all 0

        setPreferredSize(new Dimension(width+border*2, height+border*2));
        setBackground(Color.white);
        setForeground(Color.black);

        clear();

        if (glider) {
            // create an initial glider in the upper left corner
            B[0][1] = B[1][2] = B[2][0] = B[2][1] = B[2][2] = 1;
        } else if (shape != null) { // If the user specified a shape in the config file, it is added to the UI here.
	    for (Point s: shape) {
		B[s.y][s.x] = 1;
	    }
	}
    }
}

// Class UI is the user interface.  It displays a LifeBoard canvas above
// a row of buttons.  Actions (event handlers) are defined for each
// of the buttons.  Depending on the state of the UI, either the "run" or
// the "pause" button is the default (highlighted in most window
// systems); it will often self-push if you hit carriage return.
//
class UI extends JPanel {
    private final Coordinator c;
    private final LifeBoard lb;

    private final JRootPane root;
    private static final int externalBorder = 6;
 
    public boolean step_switch = false;

    private static final int stopped = 0;
    private static final int running = 1;
    private static final int paused = 2;

    private int state = stopped;

    private int numThreads;
    private int numTasks;


    final JButton runButton = new JButton("Run");
    final JButton pauseButton = new JButton("Pause");
    final JButton stopButton = new JButton("Stop");
    final JButton clearButton = new JButton("Clear");
    final JButton quitButton = new JButton("Quit");
    final JButton stepButton = new JButton("Step");// Added a button that allows the user to proceed in the game by one generation.

    // Constructor
    //
    public UI(int N, RootPaneContainer pane, int pauseIterations,
              boolean headless, boolean glider, int NT, int K, List<Point> shape) {
        final UI u = this;
        c = new Coordinator(pauseIterations);
        lb = new LifeBoard(N, c, u, headless, glider, shape);
        numThreads = NT;
        numTasks = K;

        final JPanel b = new JPanel();   // button panel

        // Note that the addListener calls below pass an annonymous
        // inner class as argument.

        lb.addMouseListener(new MouseListener() {
            public void mouseClicked(MouseEvent e) {
                if (state == stopped) {
                    lb.toggleClick(e.getX(), e.getY());
                } // else do nothing
            }
            public void mouseEntered(MouseEvent e) { }
            public void mouseExited(MouseEvent e) { }
            public void mousePressed(MouseEvent e) { }
            public void mouseReleased(MouseEvent e) { }
        });
        runButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (state == stopped) {
                    state = running;
                    root.setDefaultButton(pauseButton);
                    onRunClick();
                } else if (state == paused) {
                    state = running;
                    root.setDefaultButton(pauseButton);
                    c.toggle();
                }
            }
        });
        pauseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (state == running) {
                    state = paused;
                    root.setDefaultButton(runButton);
                    c.toggle();
                }
            }
        });
        stopButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                state = stopped;
                c.stop();
                root.setDefaultButton(runButton);
            }
        });
        clearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                state = stopped;
                c.stop();
                root.setDefaultButton(runButton);
                lb.clear();
            }
        });
        quitButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
	// This button allows the user to look at the game generation by generation.
	// If the game is currently running, this button will pause the game. 
	// Otherwise, the button "clicks on" the run button, only allowing it to go 
	// forward by one generation. This restriction is in the run method of the threads
	// themselves.
        stepButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
		step_switch = true;
  		if (state == running) {
		    pauseButton.doClick();
		} else if (state == paused || state == stopped) {
		    runButton.doClick();
		}

            }
        });

        // put the buttons into the button panel:
        b.setLayout(new FlowLayout());
        b.add(runButton);
        b.add(pauseButton);
        b.add(stopButton);
        b.add(clearButton);
        b.add(quitButton);
	b.add(stepButton);

        // put the LifeBoard canvas and the button panel into the UI:
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(
            externalBorder, externalBorder, externalBorder, externalBorder));
        add(lb);
        add(b);

        // put the UI into the Frame
        pane.getContentPane().add(this);
        root = getRootPane();
        root.setDefaultButton(runButton);
    }

    // Everytime onRunClick is called, it creates a new thread. This new thread creates the other 
    // threads which then update the board.
    public void onRunClick() {
        Delegator d = new Delegator(lb, c, this, numThreads, numTasks);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(d);
    }
}
