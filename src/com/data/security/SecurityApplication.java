package com.data.security;

import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.UIManager;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.data.security.models.IO;

public class SecurityApplication extends JFrame {

	/**
	 * The serial version ID
	 */
	private static final long serialVersionUID = -3566638261731273721L;

	/**
	 * The logger
	 */
	private Logger logger = Logger.getLogger("SecurityApplication");

	/**
	 * The resource map. It holds the map of source location to destination
	 * location. Only the file transfer from the specific source to destination is
	 * allowed. Else it would be the data breach.
	 */
	private HashMap<String, HashSet<String>> resourceMap = new HashMap<>();

	/**
	 * The SFTP log path
	 */
	private String logPath;

	/**
	 * The output directory
	 */
	private String outputDir;

	/**
	 * The mapping sheet path
	 */
	private String mappingSheetPath;

	/**
	 * Holds the current user
	 */
	private String currentUser = "";

	/**
	 * Holds the current local path
	 */
	private String currentLocalPath = "";

	/**
	 * Holds the current remote path
	 */
	private String currentRemotePath = "";

	/**
	 * The regex to fetch the local path
	 */
	private Pattern localPathRegex = Pattern.compile("^.*put \"([^\"]+)\".*$");

	/**
	 * The regex to fetch the remote path
	 */
	private Pattern remotePathRegex = Pattern.compile("^.*New directory is: \"(.*)\"$");

	/**
	 * The regex to fetch the current user
	 */
	private Pattern currentUserRegex = Pattern.compile("^.*open \"([^@]*)@.*$$");

	/**
	 * The date regex
	 */
	private Pattern dateRegex = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}).*");

	/**
	 * The date format
	 */
	private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	/**
	 * The image label
	 */
	private javax.swing.JLabel imageLabel;

	/**
	 * Shows the error popup
	 * 
	 * @param errorMessage
	 *            The error message
	 */
	private void showErrorPopup(String errorMessage, String heading) {

		JFrame jf = new JFrame();
		jf.setAlwaysOnTop(true);
		JLabel label = new JLabel(errorMessage);
		label.setFont(new Font(Font.SERIF, Font.BOLD, 15));
		label.setForeground(Color.white);
		UIManager.put("OptionPane.background", Color.red);
		UIManager.put("Panel.background", Color.red);
		JOptionPane.showMessageDialog(jf, label, heading, JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * Shows the info popup
	 * 
	 * @param infoMessage
	 *            The info message
	 */
	private void showInfoPopup(String infoMessage, String heading) {

		JFrame jf = new JFrame();
		jf.setAlwaysOnTop(true);
		JLabel label = new JLabel(infoMessage);
		label.setFont(new Font(Font.SERIF, Font.BOLD, 15));
		label.setForeground(Color.white);
		UIManager.put("OptionPane.background", new Color(0x008000));
		UIManager.put("Panel.background", new Color(0x008000));
		UIManager.put("OptionPane.messageForeground", Color.white);
		JOptionPane.showMessageDialog(jf, label, heading, JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * The monitor
	 */
	private class Monitor implements Runnable {

		boolean exit;
		Thread t;

		Monitor() {
			t = new Thread(this);
			exit = false;
			t.start();
		}

		public void run() {
			Date startTime = new Date();

			if (logPath == null || logPath.isEmpty()) { // Checking if SFTP log path is configured
				logger.log(Level.SEVERE, "SFTP log path is not set. Monitoring cannot be done.");
				return;
			}

			try (BufferedReader bufferedReader = new BufferedReader(new FileReader(new File(logPath)))) {

				while (!exit) { // Start to fetch new logs
					String line = bufferedReader.readLine();

					if (line == null) { // If line is null, it means that there are no new logs present in the file.
										// Thus we would wait for 1 second before checking for new logs again.

						Thread.sleep(1000);
					} else {
						if (line.matches("^.*open \"([^@]*)@.*$")) { // Fetching current user
							Matcher matcher = currentUserRegex.matcher(line);
							if (matcher.find()) {
								currentUser = matcher.group(1);
							}
						} else if (parseDate(line).after(startTime) && line.matches("^.*put \"([^\"]+)\".*$")) { // Fetching
																													// current
																													// local
																													// path
							Matcher matcher = localPathRegex.matcher(line);
							if (matcher.find()) {
								currentLocalPath = matcher.group(1);
								handleFileUpload();
							}
						} else if (line.matches("^.*New directory is: \"(.*)\"$")) { // Fetching current remote path
							Matcher matcher = remotePathRegex.matcher(line);
							if (matcher.find()) {
								currentRemotePath = matcher.group(1);
							}
						}
					}
				}
			} catch (IOException ioException) {
				showErrorPopup("Log file not found.", "Error");
			} catch (InterruptedException interruptedException) {
				showErrorPopup("Error reading logs file.", "Error");
			}
		}
	}

	/**
	 * Initializes the path
	 */
	private void initializePaths() {

		try {
			Properties properties = new Properties();
			properties.load(new FileInputStream(
					new File("X:\\Business Units\\Payroll\\Operations\\APAC\\Project\\SFTP\\data.properties")));
			logPath = properties.getProperty("sftp.logpath");
			outputDir = properties.getProperty("sftp.out.dir");
			mappingSheetPath = properties.getProperty("sftp.mappingsheet");
		} catch (IOException ioException) {
			showErrorPopup("data properties reading failed.", "Error");
		}
	}

	/**
	 * Instantiating the security application
	 */
	public SecurityApplication() {

		imageLabel = new javax.swing.JLabel();

		setTitle("Data Breach Squad");
		setSize(new java.awt.Dimension(520, 320));
		setResizable(false);
		getContentPane().setLayout(null);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setUndecorated(true);
		getRootPane().setWindowDecorationStyle(JRootPane.NONE);
		setLocationRelativeTo(null);

		try {
			imageLabel.setIcon(new javax.swing.ImageIcon(new javax.swing.ImageIcon(
					SecurityApplication.class.getClassLoader().getResource("databreachsquad.png")).getImage()
							.getScaledInstance(520, 320, Image.SCALE_DEFAULT)));
		} catch (Exception exception) {
			// do nothing
		}
		imageLabel.setAlignmentY(0.0F);
		imageLabel.setPreferredSize(new java.awt.Dimension(520, 320));
		getContentPane().add(imageLabel);
		imageLabel.setBounds(0, 0, 520, 320);

		initializePaths();
		initializeMappingSheet();
		new Monitor();
	}

	/**
	 * Initializes the mapping sheet
	 */
	private void initializeMappingSheet() {

		try (FileInputStream mappingFile = new FileInputStream(mappingSheetPath);
				Workbook workbook = new XSSFWorkbook(mappingFile);) {
			Sheet mappingSheet = workbook.getSheetAt(0);
			Iterator<Row> iterator = mappingSheet.iterator();

			// Iterating over the rows
			while (iterator.hasNext()) {
				Row currentRow = iterator.next();
				Iterator<Cell> cellIterator = currentRow.iterator();

				// Iterating over the columns
				while (cellIterator.hasNext()) {
					String key = cellIterator.next().toString();
					String value = cellIterator.next().toString();
					if (resourceMap.containsKey(key)) {
						resourceMap.get(key).add(value);
					} else {
						HashSet<String> newSet = new HashSet<>();
						newSet.add(value);
						resourceMap.put(key, newSet);
					}
				}
			}
		} catch (Exception exception) {
			showErrorPopup("Error reading the mapping sheet", "Error");
		}
	}

	/**
	 * Checks for data breach
	 */
	private boolean checkForDataBreach() {

		String[] localDirectories = currentLocalPath.split("\\\\");
		String[] remoteDirectories = currentRemotePath.split("/");
		StringBuilder localPathBuilder = new StringBuilder();

		for (String localDirectory : localDirectories) {
			localPathBuilder.append(localDirectory);
			StringBuilder remotePathBuilder = new StringBuilder();
			for (String remoteDirectory : remoteDirectories) {
				remotePathBuilder.append(remoteDirectory);
				String key = localPathBuilder.toString();
				if (resourceMap.containsKey(key) && resourceMap.get(key).contains(remotePathBuilder.toString())) {
					return false;
				}
				remotePathBuilder.append("/");
			}
			localPathBuilder.append("\\");
		}
		return true;
	}

	/**
	 * Parses date from the line
	 * 
	 * @param line
	 *            The line
	 * @return The date
	 */
	private Date parseDate(String line) {

		try {
			if (line.matches("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}).*")) {
				Matcher matcher = dateRegex.matcher(line);
				if (matcher.find()) {
					return simpleDateFormat.parse(matcher.group(1));
				}
			}
		} catch (ParseException parseException) {
			logger.log(Level.WARNING, "Unable to parse date from line: " + line, parseException);
		}
		return Date.from(Instant.ofEpochMilli(0));
	}

	/**
	 * Handles the file upload mechanism
	 */
	private void handleFileUpload() {

		boolean dataBreach = checkForDataBreach(); // Checking for data breach
		IO io = new IO();
		io.setUser(currentUser);
		io.setLocalPath(currentLocalPath);
		io.setRemotePath(currentRemotePath);
		io.setTime(new Date());

		if (dataBreach) {
			io.setStatus("Data breach");
			showErrorPopup("Potential breach", "DBS");
		} else {
			io.setStatus("Secure");
			showInfoPopup("Transmitted securely", "DBS");
		}

		appendToOperations(io);
	}
	
	

	/**
	 * Ends the monitoring
	 */
	public synchronized void appendToOperations(IO io) {

		try {
			String fileName = outputDir + "operations.xlsx";
			Boolean isNew = false;
			if (!Paths.get(fileName).toFile().exists()) { // Creating file if not already exists
				isNew = true;
			}

			if (isNew) { // Adding the header
				Workbook newWorkbook = new XSSFWorkbook();
				Sheet newSheet = newWorkbook.createSheet();
				Row header = newSheet.createRow(0);
				header.createCell(0).setCellValue("Time");
				header.createCell(1).setCellValue("User");
				header.createCell(2).setCellValue("Local path");
				header.createCell(3).setCellValue("Remote path");
				header.createCell(4).setCellValue("Status");
				FileOutputStream outputStream = new FileOutputStream(new File(fileName));
				newWorkbook.write(outputStream);
				outputStream.close();
				newWorkbook.close();
			}

			FileInputStream inputStream = new FileInputStream(fileName);
			Workbook workbook = new XSSFWorkbook(inputStream);
			Sheet sheet = workbook.getSheetAt(0);
			int lastRow = sheet.getLastRowNum();
			Row row = sheet.createRow(++lastRow);
			row.createCell(0).setCellValue(simpleDateFormat.format(io.getTime()));
			row.createCell(1).setCellValue(io.getUser());
			row.createCell(2).setCellValue(io.getLocalPath());
			row.createCell(3).setCellValue(io.getRemotePath());
			row.createCell(4).setCellValue(io.getStatus());

			inputStream.close();
			FileOutputStream outputStream = new FileOutputStream(new File(fileName));
			workbook.write(outputStream);
			outputStream.close();
			workbook.close();
		} catch (IOException ioException) {
			showErrorPopup("Error writing operation log to file", "Error");
		}
	}

	/**
	 * Execution starts from here
	 */
	public static void main(String[] args) {

		SecurityApplication securityApplication = new SecurityApplication();
		boolean dataBreach = true;
		
		IO io = new IO();
		io.setUser(securityApplication.currentUser);
		io.setLocalPath(securityApplication.currentLocalPath);
		io.setRemotePath(securityApplication.currentRemotePath);
		io.setTime(new Date());

		if (dataBreach) {
			io.setStatus("Data breach");
			securityApplication.showErrorPopup("Potential breach", "DBS");
		} else {
			io.setStatus("Secure");
			securityApplication.showInfoPopup("Transmitted securely", "DBS");
		}
	}
}
