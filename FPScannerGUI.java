package fpscanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
//SOURCEAFIS
import com.machinezoo.sourceafis.FingerprintImage;
import com.machinezoo.sourceafis.FingerprintMatcher;
import com.machinezoo.sourceafis.FingerprintTemplate;
import java.io.File;
//PICAM -- Legacy camera code; ENABLE RaspiStill: "Bullseye users can also still restore access to raspistill and raspivid. First, make sure the OS is up to date, then open a Terminal window and type sudo raspi-config. Go to Interface Options, then select Legacy Camera and reboot."
import uk.co.caprica.picam.Camera;
import uk.co.caprica.picam.CameraConfiguration;
import uk.co.caprica.picam.CameraException;
import uk.co.caprica.picam.CaptureFailedException;
import uk.co.caprica.picam.FilePictureCaptureHandler;
import uk.co.caprica.picam.NativeLibraryException;
import uk.co.caprica.picam.PictureCaptureHandler;
import uk.co.caprica.picam.enums.AutomaticWhiteBalanceMode;
import uk.co.caprica.picam.enums.Encoding;
import uk.co.caprica.picam.enums.ImageEffect;

import static uk.co.caprica.picam.CameraConfiguration.cameraConfiguration;
import static uk.co.caprica.picam.PicamNativeLibrary.installTempLibrary;
//jframe
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class FPScannerGUI {
    private static JFrame frame;
    private static JFrame imgFrame;
    private static JButton enrollButton, verifyButton, identifyButton;
    private final static int threshold = 35;
    private final static String filePath = "/home/fp5/Desktop/fp5/fp5-main/prints/printDB/";
    private final static String sqlUrl = "jdbc:mysql://68.185.142.224:3306/fp5";
    private final static String username = "remote";
    private final static String password = "welcome";
    private final static CameraConfiguration config = cameraConfiguration()
            .width(500)
            .height(500)
            .quality(100)
            .contrast(100)
            .brightness(40)
            .imageEffect(ImageEffect.NEGATIVE)
            .colourEffect(true, 128, 128)
            .encoding(Encoding.PNG);

    public static void main(String[] args) throws IOException, NativeLibraryException {
        installTempLibrary();
        // Create main window
        frame = new JFrame("Fingerprint 5 - Fingerprint Scanner");
        frame.setSize(300, 200);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // create panels
        // JPanel layout = new JPanel(new GridLayout(3, 1));
        enrollButton = new JButton("Enroll");
        verifyButton = new JButton("Verify");
        identifyButton = new JButton("Identify");
        // quitButton = new JButton("Menu");

        // implement enroll button
        enrollButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Handle enrollment button click
                System.out.println("Enrollment");
                String first;
                String last;

                JTextField firstNameField = new JTextField(10);
                JTextField lastNameField = new JTextField(10);

                JPanel enrollPanel = new JPanel();
                enrollPanel.add(new JLabel("First Name:"));
                enrollPanel.add(firstNameField);
                enrollPanel.add(Box.createHorizontalStrut(15));
                enrollPanel.add(new JLabel("Last Name:"));
                enrollPanel.add(lastNameField);

                int result = JOptionPane.showConfirmDialog(null, enrollPanel, "Enter First and Last Name",
                        JOptionPane.OK_CANCEL_OPTION);
                if (result == JOptionPane.OK_OPTION) {
                    first = firstNameField.getText();
                    last = lastNameField.getText();
                    String imageName = first + "_" + last + ".png"; // creates a name for the image
                    File file = new File(filePath + imageName);
                    if (file.exists()) {
                        JOptionPane.showMessageDialog(null,
                                "This an image with this name already exists and may be enrolled; continuing will overwrite the fingerprint image and may result in a duplicate profile. If you believe this is an error, contact a system administrator.");
                    }
                    JOptionPane.showMessageDialog(null,
                            "Present Biometric and press OK to take an image. Press Cancel to return to menu.");
                    String capture = "";
                    while (!capture.equals("Cancel")) {
                        int confirmResult = JOptionPane.showConfirmDialog(null, "Take Picture?", "Confirm Picture",
                                JOptionPane.OK_CANCEL_OPTION);
                        if (confirmResult == JOptionPane.OK_OPTION) {
                            try (Camera cam = new Camera(config)) {
                                if (file.exists()) {
                                    file.delete();
                                }
                                cam.takePicture(new FilePictureCaptureHandler(file), 1000);
                            } catch (CameraException | CaptureFailedException ex) {
                                ex.printStackTrace();
                            }
                            try {
                                imgWindow(file);
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                            capture = JOptionPane.showInputDialog(null,
                                    "Press OK to take another image, or Cancel to confirm image.");
                        } else {
                            capture = "Cancel";
                        }
                    }
                    if (file.exists()) {
                        FingerprintTemplate candidate;
                        try {
                            candidate = new FingerprintTemplate(
                                    new FingerprintImage(Files.readAllBytes(Paths.get(filePath + imageName))));
                            byte[] data = candidate.toByteArray();
                            // send template to DB using enrollQuery
                            enrollQuery(first, last, data);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }

                    }
                }
            }

        });
        verifyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("Verification");

                String first;
                String last;
                byte[] verification_template = null;
                JTextField firstNameField = new JTextField(10);
                JTextField lastNameField = new JTextField(10);
                JPanel verifyPanel = new JPanel();
                verifyPanel.add(new JLabel("First Name:"));
                verifyPanel.add(firstNameField);
                verifyPanel.add(Box.createHorizontalStrut(15));
                verifyPanel.add(new JLabel("Last Name:"));
                verifyPanel.add(lastNameField);
                int result = JOptionPane.showConfirmDialog(null, verifyPanel, "Enter First and Last Name",
                        JOptionPane.OK_CANCEL_OPTION);
                if (result == JOptionPane.OK_OPTION) {
                    first = firstNameField.getText();
                    last = lastNameField.getText();
                    String verification_imageName = first + "_" + last + "_ver.png";
                    ;
                    File file = new File(filePath + verification_imageName);
                    JOptionPane.showMessageDialog(null,
                            "Present Biometric and press OK to take an image. Press Cancel to return to menu.");
                    String capture = "";
                    while (!capture.equals("Cancel")) {
                        int confirmResult = JOptionPane.showConfirmDialog(null, "Take Picture?", "Confirm Picture",
                                JOptionPane.OK_CANCEL_OPTION);
                        if (confirmResult == JOptionPane.OK_OPTION) {
                            try (Camera cam = new Camera(config)) {
                                if (file.exists()) {
                                    file.delete();
                                }
                                cam.takePicture(new FilePictureCaptureHandler(file), 1000);
                            } catch (CameraException | CaptureFailedException ex) {
                                ex.printStackTrace();
                            }
                            try {
                                imgWindow(file);
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                            capture = JOptionPane.showInputDialog(null,
                                    "Press OK to take another image, or Cancel to confirm image.");
                        } else {
                            capture = "Cancel";
                        }
                    }
                    // biometric data
                    FingerprintTemplate candidate_verification;
                    try {
                        candidate_verification = new FingerprintTemplate(
                                new FingerprintImage(Files.readAllBytes(Paths.get(filePath + verification_imageName))));
                        // gather template data from DB
                        ResultSet verification_data = retrieveQuery(first, last);
                        try {
                            if (verification_data.next()) {
                                verification_template = verification_data.getBytes("template");
                            }
                        } catch (SQLException e1) {
                            e1.printStackTrace();
                        }
                        FingerprintTemplate ver_candidate = new FingerprintTemplate(verification_template);
                        // compare template
                        FingerprintMatcher matcher = new FingerprintMatcher(candidate_verification);
                        double similarity = matcher.match(ver_candidate);
                        System.out.println("The similarity score is : " + similarity + ".");
                        if (similarity >= threshold) {
                            JOptionPane.showMessageDialog(null,
                                    "The similarity score is : " + similarity + ".\n" + "You are a Match!");
                        }
                        if (similarity < threshold) {
                            JOptionPane.showMessageDialog(null,
                                    "The similarity score is : " + similarity + ".\n" + "You are not a Match.");
                        }
                        // output similarity score and match status.
                        // delete verification fingerprint image
                        file.delete();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
        // implement identify button
        identifyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("Identify");
                File identification_file = new File(filePath + "identify.png");
                List<Double> scores = new ArrayList<Double>();
                List<Integer> user_ids = new ArrayList<Integer>();
                List<String> identify_first = new ArrayList<String>();
                List<String> identify_last = new ArrayList<String>();
                JOptionPane.showMessageDialog(null,
                        "Present Biometric and press OK to take an image. Press Cancel to return to menu.");
                String capture = "";
                while (!capture.equals("Cancel")) {
                    int confirmResult = JOptionPane.showConfirmDialog(null, "Take Picture?", "Confirm Picture",
                            JOptionPane.OK_CANCEL_OPTION);
                    if (confirmResult == JOptionPane.OK_OPTION) {
                        try (Camera cam = new Camera(config)) {
                            if (identification_file.exists()) {
                                identification_file.delete();
                            }
                            cam.takePicture(new FilePictureCaptureHandler(identification_file), 1000);
                        } catch (CameraException | CaptureFailedException ex) {
                            ex.printStackTrace();
                        }
                        try {
                            imgWindow(identification_file);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        capture = JOptionPane.showInputDialog(null,
                                "Press OK to take another image, or Cancel to confirm image.");
                    } else {
                        capture = "Cancel";
                    }
                }
                try {
                    FingerprintTemplate identity_probe = new FingerprintTemplate(
                            new FingerprintImage(Files.readAllBytes(Paths.get(filePath + "identify.png"))));
                    ResultSet candidates = identifyQuery();
                    // delete img after creating a template
                    identification_file.delete();
                    int i = 0;
                    while (candidates.next()) {
                        user_ids.add(i, candidates.getInt("user_id"));
                        identify_first.add(i, candidates.getString("fname"));
                        identify_last.add(i, candidates.getString("lname"));
                        FingerprintTemplate identity_candidate = new FingerprintTemplate(
                                candidates.getBytes("template"));
                        FingerprintMatcher identity_matcher = new FingerprintMatcher(identity_probe);
                        scores.add(i, identity_matcher.match(identity_candidate));
                        System.out.println("Similarity score: " + scores.get(i) + " || " + identify_first.get(i) + " "
                                + identify_last.get(i));
                        i++;
                    }
                    double max = Double.NEGATIVE_INFINITY;
                    int index = Integer.MIN_VALUE;
                    int j = 0;
                    for (double temp : scores) {
                        if (scores.get(j) > max) {
                            max = scores.get(j);
                            index = j;
                        }
                        j++;
                    }
                    if (max < threshold) {
                        JOptionPane.showMessageDialog(null, "The closest user is : " + identify_first.get(index) + " "
                                + identify_last.get(index) + ", but no definitive matches were found.");
                    } else {
                        JOptionPane.showMessageDialog(null,
                                "The user is : " + identify_first.get(index) + " " + identify_last.get(index));
                    }
                } catch (IOException | SQLException e1) {
                    e1.printStackTrace();
                }

            }
        });
    }

    /** Enroll a user into the database **/
    public static void enrollQuery(String fname, String lname, byte[] data) {
        try {
            // int result
            Connection conn = DriverManager.getConnection(sqlUrl, username, password);
            PreparedStatement stmt = conn
                    .prepareStatement("INSERT INTO users (fname, lname, template) VALUES (?, ?, ?)");
            stmt.setString(1, fname);
            stmt.setString(2, lname);
            stmt.setBytes(3, data);
            stmt.executeUpdate();
            System.out.println("User enrolled successfully.");
            conn.close();
        } catch (SQLException e) {
            System.out.println("Error inserting user: " + e.getMessage());
        }
    }

    /** Retrieve the template from a specific user from the database **/
    public static ResultSet retrieveQuery(String fname, String lname) {
        ResultSet result = null;
        try {
            Connection conn = DriverManager.getConnection(sqlUrl, username, password);
            PreparedStatement stmt = conn.prepareStatement("SELECT template FROM users WHERE fname = ? and lname = ?");
            stmt.setString(1, fname);
            stmt.setString(2, lname);
            result = stmt.executeQuery();
            System.out.println("Verification Data Successfully Retrieved.");
        } catch (SQLException e) {
            System.out.println("Error retrieving user: " + e.getMessage());
        }
        return result;
    }

    /** Retrieve all users in the Database */
    public static ResultSet identifyQuery() {
        ResultSet result = null;
        try {
            Connection conn = DriverManager.getConnection(sqlUrl, username, password);
            PreparedStatement stmt = conn.prepareStatement("SELECT user_id, fname, lname, template FROM users");
            result = stmt.executeQuery();
            System.out.println("Identification Data Successfully Retrieved.");
        } catch (SQLException e) {
            System.out.println("Error retrieving user(s): " + e.getMessage());
        }
        return result;
    }

    // generate an image window
    public static void imgWindow(File file) throws IOException {
        // close the window if already open
        if (imgFrame != null) {
            imgFrame.dispose();
        }
        // create an image var
        Image img = ImageIO.read(file);
        // create a jframe window and display the image
        // frame is a static var in order to close window when new image is created
        imgFrame = new JFrame(null, null);
        imgFrame.setAutoRequestFocus(false);
        JLabel label = new JLabel(new ImageIcon(img));
        imgFrame.add(label);
        imgFrame.pack();
        imgFrame.setVisible(true);
    }

}
