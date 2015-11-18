package amidst.gui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import amidst.AmidstMetaData;
import amidst.Application;
import amidst.Options;
import amidst.Util;
import amidst.gui.menu.AmidstMenu;
import amidst.gui.menu.LevelFileFilter;
import amidst.gui.menu.PNGFileFilter;
import amidst.logging.Log;
import amidst.map.Map;
import amidst.map.MapMovement;
import amidst.map.MapViewer;
import amidst.map.MapZoom;
import amidst.minecraft.MinecraftUtil;
import amidst.minecraft.world.WorldType;

public class MapWindow {
	private Application application;

	private SeedPrompt seedPrompt = new SeedPrompt();
	private MapZoom mapZoom = new MapZoom();
	private MapMovement mapMovement = new MapMovement();

	private ScheduledExecutorService executor = Executors
			.newSingleThreadScheduledExecutor();

	private Map map;
	private MapViewer mapViewer;

	private JFrame frame = new JFrame();
	private AmidstMenu menuBar;
	private Container contentPane;

	public MapWindow(Application application) {
		this.application = application;
		initFrame();
		initContentPane();
		initMenuBar();
		initKeyListener();
		initCloseListener();
		showFrame();
		checkForUpdates();
		startExecutor();
	}

	private void initFrame() {
		frame.setTitle(getVersionString());
		frame.setSize(1000, 800);
		frame.setIconImage(AmidstMetaData.ICON);
	}

	private String getVersionString() {
		if (MinecraftUtil.hasInterface()) {
			return "Amidst v" + AmidstMetaData.getFullVersionString()
					+ " [Using Minecraft version: "
					+ MinecraftUtil.getVersion() + "]";
		} else {
			return "Amidst v" + AmidstMetaData.getFullVersionString();
		}
	}

	private void initContentPane() {
		contentPane = frame.getContentPane();
		contentPane.setLayout(new BorderLayout());
	}

	private void initMenuBar() {
		menuBar = new AmidstMenu(application, this);
		frame.setJMenuBar(menuBar.getMenuBar());
	}

	private void initKeyListener() {
		frame.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (mapViewer != null) {
					Point mouse = mapViewer.getMousePositionOrCenter();
					if (e.getKeyCode() == KeyEvent.VK_EQUALS) {
						mapZoom.adjustZoom(mouse, -1);
					} else if (e.getKeyCode() == KeyEvent.VK_MINUS) {
						mapZoom.adjustZoom(mouse, 1);
					}
				}
			}
		});
	}

	private void initCloseListener() {
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				frame.dispose();
				System.exit(0);
			}
		});
	}

	private void showFrame() {
		frame.setVisible(true);
	}

	private void checkForUpdates() {
		application.checkForUpdatesSilently();
	}

	private void startExecutor() {
		executor.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				if (mapViewer != null) {
					mapViewer.repaint();
				}
			}
		}, 20, 20, TimeUnit.MILLISECONDS);
	}

	public void dispose() {
		clearWorld();
		frame.dispose();
		shutdownExecutor();
	}

	private void shutdownExecutor() {
		executor.shutdown();
		try {
			executor.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Log.w("MapWindow executor shutdown took too longer.");
			e.printStackTrace();
		}
	}

	private void clearWorld() {
		if (mapViewer != null) {
			menuBar.disableMapMenu();
			contentPane.remove(mapViewer.getPanel());
			map.safeDispose();
			mapMovement.reset();
			mapZoom.skipFading();
			mapViewer = null;
			map = null;
		}
	}

	private void initWorld() {
		map = new Map(application.getFragmentManager(), mapZoom);
		mapViewer = new MapViewer(mapMovement, mapZoom, application.getWorld(),
				application.getLayerContainer(), map);
		menuBar.enableMapMenu();
		contentPane.add(mapViewer.getPanel(), BorderLayout.CENTER);
		frame.validate();
	}

	public String askForSeed() {
		return seedPrompt.askForSeed(frame);
	}

	@SuppressWarnings("unchecked")
	public <T> T askForOptions(String title, String message, T[] choices) {
		return (T) JOptionPane.showInputDialog(frame, message, title,
				JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]);
	}

	public File askForMinecraftMapFile() {
		return getSelectedFileOrNull(createMinecraftMapFileChooser());
	}

	private JFileChooser createMinecraftMapFileChooser() {
		JFileChooser result = new JFileChooser();
		result.setFileFilter(new LevelFileFilter());
		result.setAcceptAllFileFilterUsed(false);
		result.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		result.setCurrentDirectory(Util.getSavesDirectory());
		result.setFileHidingEnabled(false);
		return result;
	}

	public File askForScreenshotSaveFile() {
		return getSelectedFileOrNull(createScreenshotSaveFileChooser());
	}

	private JFileChooser createScreenshotSaveFileChooser() {
		JFileChooser result = new JFileChooser();
		result.setFileFilter(new PNGFileFilter());
		result.setAcceptAllFileFilterUsed(false);
		return result;
	}

	private File getSelectedFileOrNull(JFileChooser fileChooser) {
		if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
			return fileChooser.getSelectedFile();
		} else {
			return null;
		}
	}

	public void displayMessage(String message) {
		JOptionPane.showMessageDialog(frame, message);
	}

	public void displayException(Exception exception) {
		displayMessage(getStackTraceAsString(exception));
	}

	private String getStackTraceAsString(Exception exception) {
		StringWriter writer = new StringWriter();
		exception.printStackTrace(new PrintWriter(writer));
		return writer.toString();
	}

	public int askToConfirm(String title, String message) {
		return JOptionPane.showConfirmDialog(frame, message, title,
				JOptionPane.YES_NO_OPTION);
	}

	public void moveMapToCoordinates(long x, long y) {
		map.safeCenterOn(x, y);
	}

	public WorldType askForWorldType() {
		String worldTypePreference = Options.instance.worldType.get();
		if (worldTypePreference.equals("Prompt each time")) {
			return askForOptions("New Project", "Enter world type\n",
					WorldType.getSelectable());
		} else {
			return WorldType.from(worldTypePreference);
		}
	}

	public void worldChanged() {
		clearWorld();
		initWorld();
	}

	public void capture(File file) {
		BufferedImage image = mapViewer.createCaptureImage();
		saveToFile(image, file);
		image.flush();
	}

	private void saveToFile(BufferedImage image, File file) {
		try {
			ImageIO.write(image, "png", appendPNGFileExtensionIfNecessary(file));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private File appendPNGFileExtensionIfNecessary(File file) {
		String filename = file.toString();
		if (!filename.toLowerCase().endsWith(".png")) {
			filename += ".png";
		}
		return new File(filename);
	}

	public long[] askForCoordinates() {
		String coordinates = askForString("Go To",
				"Enter coordinates: (Ex. 123,456)");
		if (coordinates != null) {
			return parseCoordinates(coordinates);
		} else {
			return null;
		}
	}

	private String askForString(String title, String message) {
		return JOptionPane.showInputDialog(frame, message, title,
				JOptionPane.QUESTION_MESSAGE);
	}

	private long[] parseCoordinates(String coordinates) {
		String[] parsedCoordinates = coordinates.replaceAll(" ", "").split(",");
		if (parsedCoordinates.length != 2) {
			return null;
		}
		try {
			long[] result = new long[2];
			result[0] = Long.parseLong(parsedCoordinates[0]);
			result[1] = Long.parseLong(parsedCoordinates[1]);
			return result;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public void repaintBiomeLayer() {
		if (map != null) {
			map.repaintBiomeLayer();
		}
	}
}
