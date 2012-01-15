package necromunda;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Observable;
import java.util.Set;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

public class Necromunda extends Observable {
	public enum SelectionMode {
		DEPLOY,
		SELECT,
		MOVE,
		CLIMB,
		TARGET,
		REROLL
	}
	
	public enum Phase {
		MOVEMENT("Movement"),
		SHOOTING("Shooting"),
		HAND_TO_HAND("Hand to Hand"),
		RECOVERY("Recovery");
		
		private String literal;
		
		private Phase(String literal) {
			this.literal = literal;
		}

		@Override
		public String toString() {
			return literal;
		}
	}
	
	public static final float RUN_SPOT_DISTANCE = 8;
	public static final float UNPIN_BY_INITIATIVE_DISTANCE = 2;
	public static final float SUSTAINED_FIRE_RADIUS = 4;
	public static final float STRAY_SHOT_RADIUS = 0.5f;
	public static Necromunda game;
	private static String statusMessage;
	
	private List<Gang> gangs;
	private Map<String, List<Building>> maps;
	private Fighter selectedFighter;
	private LinkedList<Fighter> deployQueue;
	private SelectionMode selectionMode;
	private Gang currentGang;
	private int turn;
	private Phase phase;
	
	private JFrame necromundaFrame;
	
	public static final int[][] STRENGTH_RESISTANCE_MAP = {
		{4, 5, 6, 6, 7, 7, 7, 7, 7, 7},
		{3, 4, 5, 6, 6, 7, 7, 7, 7, 7},
		{2, 3, 4, 5, 6, 6, 7, 7, 7, 7},
		{2, 2, 3, 4, 5, 6, 6, 7, 7, 7},
		{2, 2, 2, 3, 4, 5, 6, 6, 7, 7},
		{2, 2, 2, 2, 3, 4, 5, 6, 6, 7},
		{2, 2, 2, 2, 2, 3, 4, 5, 6, 6},
		{2, 2, 2, 2, 2, 2, 3, 4, 5, 6},
		{2, 2, 2, 2, 2, 2, 2, 3, 4, 5},
		{2, 2, 2, 2, 2, 2, 2, 2, 3, 4},
	};
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Necromunda game = Necromunda.getInstance();
	}
	
	public Necromunda() {
		gangs = new ArrayList<Gang>();
		selectionMode = SelectionMode.DEPLOY;
		turn = 1;
		statusMessage = "";
		maps = createBuildings();
		
		deployQueue = new LinkedList<Fighter>();
		
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				initialiseGUI();
			}
			
		});
	}
	
	public static Necromunda getInstance() {
		if (game == null) {
			game = new Necromunda();
		}
		
		return game;
	}
	
	private Map<String, List<Building>> createBuildings() {
		Map<String, List<Building>> maps = new HashMap<String, List<Building>>();
		
		List<Building> buildings = new ArrayList<Building>();
		
		buildings.add(new Building(FastMath.PI / 4, new Vector3f(8, 0, 8), "01"));
		buildings.add(new Building(FastMath.PI / -8, new Vector3f(40, 0, 8), "01"));
		buildings.add(new Building(FastMath.PI / 2, new Vector3f(6, 0, 40), "01"));
		buildings.add(new Building(0, new Vector3f(24, 0, 22), "03"));
		buildings.add(new Building(0, new Vector3f(38, 0, 40), "01"));
		buildings.add(new Building(FastMath.PI / 8 * 3, new Vector3f(8, 0, 24), "02"));
		buildings.add(new Building(FastMath.PI / 8 * -3, new Vector3f(40, 0, 24), "02"));

		maps.put("Courtyard", buildings);
		
		buildings = new ArrayList<Building>();
		
		buildings.add(new Building(FastMath.PI / 6, new Vector3f(8, 0, 8), "01"));
		buildings.add(new Building(FastMath.PI / 3, new Vector3f(6, 0, 40), "01"));
		buildings.add(new Building(FastMath.PI / 8, new Vector3f(24, 0, 22), "04"));
		buildings.add(new Building(FastMath.PI / 8, new Vector3f(24, 0, 22), "05"));
		buildings.add(new Building(0, new Vector3f(38, 0, 40), "01"));
		buildings.add(new Building(FastMath.PI / 5 * 3, new Vector3f(8, 0, 24), "02"));
		
		maps.put("Steel Quarter", buildings);
		
		return maps;
	}
	
	private void initialiseGUI() {
		GangGenerationPanel gangGenerationPanel = new GangGenerationPanel(this);
		
		necromundaFrame = new JFrame("Necromunda");
		necromundaFrame.setSize(1000, 800);
		necromundaFrame.setResizable(false);
		necromundaFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		necromundaFrame.setLocationRelativeTo(null);
		necromundaFrame.add(gangGenerationPanel);
		necromundaFrame.setVisible(true);
	}
	
	public void fighterDeployed() {
		selectedFighter = deployQueue.poll();
		
		if (selectedFighter == null) {
			currentGang = gangs.get(0);
			currentGang.turnStarted();
			selectionMode = SelectionMode.SELECT;
			phase = Phase.MOVEMENT;
		}
	}
	
	public void endTurn() {
		currentGang.turnEnded();
		
		if (((gangs.indexOf(currentGang) + 1) % gangs.size()) == 0) {
			turn++;
		}
		
		currentGang = getNextGang();

		currentGang.turnStarted();
		
		selectedFighter = null;
		
		selectionMode = SelectionMode.SELECT;
		
		phase = Phase.MOVEMENT;
	}
	
	private Gang getNextGang() {
		Gang gang = null;
		int gangIndex = gangs.indexOf(currentGang);
		
		if ((gangIndex + 1) == gangs.size()) {
			gang = gangs.get(0);
		}
		else {
			gang = gangs.get(gangIndex + 1);
		}
		
		return gang;
	}
	
	public void nextPhase() {
		switch (phase) {
			case MOVEMENT:
				phase = Phase.SHOOTING;
				break;
			case SHOOTING:
			case HAND_TO_HAND:
			case RECOVERY:
		}
		
		selectionMode = SelectionMode.SELECT;
	}
	
	public void updateStatus() {
		setChanged();
		notifyObservers();
	}
	
	public void commitGeneratedGangs(Enumeration<?> gangs) {		
		while (gangs.hasMoreElements()) {
			Gang nextGang = (Gang)gangs.nextElement();
			
			deployQueue.addAll(nextGang.getGangMembers());
			
			if (getCurrentGang() == null) {
				setCurrentGang(nextGang);
				setSelectedFighter(deployQueue.poll());
			}
			
			getGangs().add(nextGang);
		}
	}

	public Gang getCurrentGang() {
		return currentGang;
	}

	public void setCurrentGang(Gang currentGang) {
		this.currentGang = currentGang;
	}

	public Fighter getSelectedFighter() {
		return selectedFighter;
	}

	public void setSelectedFighter(Fighter selectedFighter) {
		this.selectedFighter = selectedFighter;
	}

	public List<Gang> getGangs() {
		return gangs;
	}

	public JFrame getNecromundaFrame() {
		return necromundaFrame;
	}

	public SelectionMode getSelectionMode() {
		return selectionMode;
	}

	public void setSelectionMode(SelectionMode selectionMode) {
		this.selectionMode = selectionMode;
	}

	public int getTurn() {
		return turn;
	}
	
	public Phase getPhase() {
		return phase;
	}

	public void setPhase(Phase phase) {
		this.phase = phase;
	}

	public static String getStatusMessage() {
		return statusMessage;
	}

	public static void setStatusMessage(String statusMessage) {
		Necromunda.statusMessage = statusMessage;
	}
	
	public static void appendToStatusMessage(String statusMessage) {
		if (Necromunda.statusMessage.equals("")) {
			Necromunda.statusMessage = statusMessage;
		}
		else {
			Necromunda.statusMessage = String.format("%s %s", Necromunda.statusMessage, statusMessage);
		}
	}
	
	public List<Fighter> getHostileGangers() {
		return currentGang.getHostileGangers(gangs);
	}

	public Map<String, List<Building>> getMaps() {
		return maps;
	}
}
