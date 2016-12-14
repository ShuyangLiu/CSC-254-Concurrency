/*
    Life.java

    Graphical implementation of Conway's game of Life.

    Currently single-threaded, but has infrastructure for multithreaded
    solutions.

    Michael L. Scott, November 2016, based on earlier versions from
    1998, 2007, and 2011.

    Edited by Shuyang Liu and Nina Bose in 2016.
 */

import java.awt.*;          // older of the two standard Java GUIs
import java.awt.event.*;
import javax.swing.*;
import java.lang.Thread.*;
import java.util.List;
import java.util.ArrayList;
import java.io.*;

public class Life {

	public static volatile long start_time;
	public static volatile long end_time;

    private static final int n = 100;    // number of cells on a side
    private static int pauseIterations = -(500000000/n/n);
        // nanoseconds per dot for a delay of about a half a second
    public static long numThreads = 1;

    private static boolean headless = false;    // don't create GUI
    private static boolean glider = false;      // create initial glider
    private static List<Point> shape = null;	// used to represent a custom shape specified by the user in a config file.

    private static UI u; // store the UI in Life

    public static volatile int counter = 0; // Thread counter

    // Helper method to create the UI. 
    private void buildUI(RootPaneContainer pane, List<Point> shape) {
        u = new UI(n, pane, pauseIterations, headless, glider, numThreads, shape);
    }

    // List of worker threads.
    private static ArrayList<Worker> worker_list;

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
		   } else { System.err.println("Could not configure from file. Using default values instead.");}
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
            } else if (args[i].equals("--headless")) {
                headless = true;
            } else if (args[i].equals("--glider")) {
                glider = true;
            } else {
                die(String.format("Unexpected argument: %s\n", args[i]));
            }
        }
    }

    // Creates list of workers and assigns each of them a task.
    public static void initializeWorkers() {
    	double begin = 0;
	double interval = (n * 1.0 /numThreads);
        double end = interval;

        worker_list = new ArrayList<>();
	// Provide each thread with the same number of contiguous rows to look at as all other threads,
	// give or take up to one row.
        for(int i=0; i<numThreads; i++) {
        	Worker w = new Worker(u.getLifeBoard(), u.getCoordinator(), u); // making a new thread
		if(end >= n-1) {
			end = n;
		}
        	w.setTask((int)begin, (int)end);
        	worker_list.add(w); // add to worker_list to be ready to use
        	begin += interval;
        	end += interval;
        }
    }

    public static void main(String[] args)
    {
        parseArgs(args);
        Life me = new Life();

        JFrame f = new JFrame("Life");
        f.addWindowListener(new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            System.exit(0);
          }
        });
        me.buildUI(f, shape);
        initializeWorkers();
        u.t_list = worker_list; // give a reference to the thread list

        if (headless) {
            u.onRunClick(worker_list);
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

// Represents the range of rows that a thread should update.
// start_index is inclusive and end_index is exclusive. In other words,
// the range looks like, [start_index, end_index).
class Task {
	int start_index; // First row to be updated (inclusive).
	int end_index;   // First row after last row that should be update. 
	public Task(int s, int e) {
		start_index = s;
		end_index = e;
	}
}

// The Worker is the thread that does the actual work of calculating new
// generations.
//
class Worker extends Thread {
    private final LifeBoard lb;
    private final Coordinator c;
    private final UI u;

    private Task t; // Each thread has a task assigned to it, i.e., 
		    // a range of rows that the thread should update.

    // The run() method of a Java Thread is never invoked directly by
    // user code.  Rather, it is called by the Java runtime when user
    // code calls start().
    public void run() {
        try {
            c.register();
            while (true) {
                lb.doGeneration(t.start_index, t.end_index);
		// Each thread updates around n/numThreads rows on the board. 
		// However, they are only updated once the last thread has finished
		// updating the board, which we keep track of by using a counter.
                synchronized (c) {
                	Life.counter= Life.counter+1;
	                if(Life.counter < Life.numThreads) {
			                try {
			                	c.wait();// wait until other threads are done with current generation
			                }catch (InterruptedException e) {
			                	e.printStackTrace();
			                }
	                } else {
	                	//If this is the last thread
	                	//Life.end_time = System.nanoTime();
	                        Life.counter = 0; // reset counter to zero
	                	lb.updateBoard(); // update the board
        	                //pause if it is in step mode
	                	c.notifyAll(); // notify all the threads that are waiting to proceed
				// This if statement allows us to play one generation at a time by pausing
				// the game after one generation has been completed.
	                        if(u.step_switch == true) {
        	                    u.pauseButton.doClick();
                	        }
			u.step_switch = false;
	                }
                }
            }
        }
        catch(Coordinator.KilledException e) {}
        finally {
            c.unregister();
        }
    }

    // Constructor
    public Worker(LifeBoard LB, Coordinator C, UI U) {
        lb = LB;
        c = C;
        u = U;
    }

    public void setTask(int s, int e) {
    	this.t = new Task(s,e);
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
    private volatile int A[][];  // scratch board
    private int T[][];  // temporary pointer
    private int generation = 0;

    // following fields are set by constructor:
    private final Coordinator c;
    private final UI u;
    private final int n;  // number of cells on a side

    public int getN() {
    	return n;
    }

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

    public int getGeneration() {
        return generation;
    }

    // This is the function that actually plays (one full generation of)
    // the game.  It is called by the run() method of Thread class
    // Worker.
    //
    // We split the original method into two separate methods, doGeneration and updateBoard.
    // Instead of updating the entire board at once, each thread updates some number of rows. 
    public void doGeneration(int start, int end) throws Coordinator.KilledException {
        for (int i = start; i < end; i++) {
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
    public void updateBoard() throws Coordinator.KilledException{
    	    c.hesitate();
	    T = B;  B = A;  A = T;
	    if (headless) {
	    	if (generation % 10 == 0) {
	    		System.out.print(System.currentTimeMillis() + ", ");
	    	}
		++generation;
	    } else {
            //System.out.println(Color_Code.wrap("[DEBUG] updateBoard: "+SwingUtilities.isEventDispatchThread(),225));
		repaint ();
                ++generation;
	    }

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

    // Returns a representation of the board in which each point represents an occupied spot.
    public List<Point> getPoints() {
	List<Point> points = new ArrayList<>();
	for(int i = 0; i < n; i++) {
	    for(int j = 0; j < n; j++) {
		if (B[i][j] == 1) {
		    points.add(new Point(j, i));
		}
  	    }
	}
	return points;
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

    public volatile ArrayList<Worker> t_list;

    private final JRootPane root;
    private static final int externalBorder = 6;

    private static final int stopped = 0;
    private static final int running = 1;
    private static final int paused = 2;

    private int state = stopped;

    public boolean step_switch = false;
    public long numThreads;
    public final String outputFile = "output_config.txt";

    public final JButton runButton ;
    public final JButton pauseButton ;
    public final JButton stopButton ;
    public final JButton clearButton ;
    public final JButton quitButton ;
    public final JButton stepButton ; // Added a button that allows the user to proceed in the game by one generatio 
    public final JButton configButton; // Added a button that allows the user to get the current configuration of the board, so long as the game is paused or stopped.

    public LifeBoard getLifeBoard() // a getter method for lb
    {
    	return lb;
    }
    public Coordinator getCoordinator() { // a getter method for c
    	return c;
    }

    // Constructor
    //
    public UI(int N, RootPaneContainer pane, int pauseIterations,
              boolean headless, boolean glider, long NT, List<Point> shape) {
        final UI u = this;
        c = new Coordinator(pauseIterations);
        lb = new LifeBoard(N, c, u, headless, glider, shape);
	
	numThreads = NT;

        final JPanel b = new JPanel();   // button panel

        runButton = new JButton("Run");
        pauseButton = new JButton("Pause");
        stopButton = new JButton("Stop");
        clearButton = new JButton("Clear");
        quitButton = new JButton("Quit");
        stepButton = new JButton("Step"); 
	configButton = new JButton("Get Configuration"); 

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

                    // reset t_list
                    t_list = new ArrayList<>();
                    double s = 0;
                    double r =  ((lb.getN()*1.0)/(Life.numThreads*1.0));
                    for(int i=0; i<Life.numThreads; i++) {
                    	Worker w = new Worker(lb, c, u); // making a new thread
            			if(r >= lb.getN()-1){
            				r = lb.getN();
            			}	
                    	w.setTask((int)s, (int)r);
                    	t_list.add(w); // add to worker_list to be ready to use
                    	s += (lb.getN()*1.0)/(1.0*Life.numThreads);
                    	r +=  (lb.getN()*1.0)/(1.0*Life.numThreads);
                    }

                    onRunClick(t_list);
                } else if (state == paused) {
                    state = running;
                    root.setDefaultButton(pauseButton);
                    c.toggle();
                }
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
                } else if (state == paused) {
                    runButton.doClick();
                } else if (state == stopped) {
                    runButton.doClick();
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
                //System.out.println(Color_Code.wrap("[DEBUG] updateBoard: "+SwingUtilities.isEventDispatchThread(),225));
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
	// Thus buggon allows the user to get the current configuration of the board, so long
	// as the game is currently paused or stopped. It will create a file called output_config.txt
	// and store the configuration in there.
	configButton.addActionListener(new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		if (state == running) {
		    System.err.println("Game must be paused or stopped in order to get configuration.");
		} else if (state == paused || state == stopped) {
		    try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))) {
			bw.write(buildConfigString(pauseIterations));
		    } catch (IOException ex) { System.err.println("Error: could not create config file."); }
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
	b.add(configButton);


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

    // Builds the string that contains all of the information to be put in the config file.
    public String buildConfigString(int pauseIterations) {
	StringBuilder content = new StringBuilder();
	content.append("t:");
	content.append(numThreads);
	content.append("\n");
	content.append("s:");
	content.append(pauseIterations);
	content.append("\n");
	content.append("shape:");
	List<Point> points = lb.getPoints();
	for(Point p : points) {
	    content.append(p);
	    content.append(";");
	}
	content.append("\n");
	return content.toString();
    }

    // onRunClick starts all of the threads passed into it.
    public void onRunClick(ArrayList<Worker> t_l) {
    	for(int i=0; i<t_l.size(); i++) {
    		t_l.get(i).start();
    	}
    }
}
