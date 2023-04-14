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
import static uk.co.caprica.picam.CameraConfiguration.cameraConfiguration;
import static uk.co.caprica.picam.PicamNativeLibrary.installTempLibrary;
//jframe
import java.awt.Image;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
public class FPScanner {
	private static JFrame frame = null;
	static Scanner input = new Scanner(System.in);
	public static void main(String[] args) throws IOException, NativeLibraryException {
		int mode = 4;
		//camera Configuration
		installTempLibrary();
		CameraConfiguration config =  cameraConfiguration()
		.width(500)
		.height(500)
		.encoding(Encoding.PNG)
		.quality(100)
		.saturation(0);
		
		//Camera Object --delete
		try(Camera cam = new Camera(config)){
			cam.takePicture(new FilePictureCaptureHandler(new File("picam1.png")));
		}
		catch(CameraException e){
			e.printStackTrace();
		}
		catch(CaptureFailedException e){
			e.printStackTrace();
		}

		do {
			/**MENU**/
			System.out.println("Enter 1 for Enrollment, 2 for Verification, 3 for Identification, or 0 to quit.");
			try {
			mode = input.nextInt();
			}catch(Exception e) {
				System.out.println(e.getMessage());
			}
			switch (mode) {
			case 0: System.out.print("Program Terminated.");
					break;
			case 1: System.out.println("Enrollment");
					String first;
					String last;
					String filePath = "/home/fp5/prints/"; // enter the filepath of prints folder
					Scanner name = new Scanner(System.in);
					System.out.println("Enter your first name: ");
					first = name.nextLine();
					System.out.println("Enter your last name: ");
					last = name.nextLine();
					String imageName = first + "_" + last; //creates a name for the image
					File file = new File(filePath+imageName);
					if(file.exists()){
						System.out.println("This profile is already enrolled. If you believe this is an error, contact a system administrator.");
						break;
					}
					//Camera implementation 
					System.out.println("Present Biometric and press the space-bar to take an image. Press q to return to menu.");
					String capture = "";
					Scanner select = new Scanner(System.in);
					while(capture != "Q" || capture != "q"){
						capture = select.nextLine();
						if (capture == " "){
							printScanMessage();
							try(Camera cam = new Camera(config)){
								if(file.exists()){
									file.delete();
								}
								cam.takePicture(new FilePictureCaptureHandler(file));
							}
							catch(CameraException | CaptureFailedException e){
								e.printStackTrace();
							}
							imgWindow(file);
							System.out.println("Press the space bar to take another image, or 'q' to confirm image and quit.");
						}
					}
					if(file.exists()){
						FingerprintTemplate candidate = new FingerprintTemplate(new FingerprintImage(Files.readAllBytes(Paths.get(filePath + imageName))));
						byte[] data = candidate.toByteArray();
						//send template to DB using enrollQuery
						enrollQuery(first,last,data);
					}
					
					break;
			case 2: System.out.println("Verification");
					String first_name;
					String last_name;
					byte[] verification_template = null;
					//call for user input
					Scanner verification_name = new Scanner(System.in);
					System.out.println("Enter your first name: ");
					first_name = verification_name.nextLine();
					System.out.println("Enter your last name: ");
					last_name = verification_name.nextLine();
					//TODO get camera implementation here
					//call for user to present biometric
					printScanMessage();
					//placeholder biometric data
					FingerprintTemplate candidate_verification = new FingerprintTemplate(
							   new FingerprintImage(Files.readAllBytes(Paths.get("prints/probe.png"))));
					//gather template data from DB
					ResultSet verification_data = retrieveQuery(first_name,last_name);
					try {
						if(verification_data.next()) {
							verification_template = verification_data.getBytes("template");
						}
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					FingerprintTemplate ver_candidate = new FingerprintTemplate(verification_template);
						//compare template
					FingerprintMatcher matcher = new FingerprintMatcher(candidate_verification);
					double similarity = matcher.match(ver_candidate);
					System.out.println("The similarity score is : " + similarity + ".");
					if(similarity>=40){System.out.println("You are a Match!");}	
					if(similarity<40){System.out.println("You are not a Match.");}	
					//output similarity score and match status.
					//get fingerprint
					break;
			case 3: 
					List<Double> scores = new ArrayList<Double>();
					List<Integer> user_ids = new ArrayList<Integer>();
					List<String> identify_first = new ArrayList<String>();
					List<String> identify_last = new ArrayList<String>();
					System.out.println("Identification");
					//call for biometric data
					System.out.println("Enter fingerprint image name to be identified.");
					Scanner s = new Scanner(System.in);
					String identifier = s.nextLine();
					printScanMessage();
					//placeholder biometric
					FingerprintTemplate identity_probe = new FingerprintTemplate(
							   new FingerprintImage(Files.readAllBytes(Paths.get("prints/" + identifier))));
					ResultSet candidates = identifyQuery();
					try {
						int i = 0;
						while(candidates.next()) {
							user_ids.add(i, candidates.getInt("user_id"));
							identify_first.add(i, candidates.getString("fname"));
							identify_last.add(i, candidates.getString("lname"));
							FingerprintTemplate identity_candidate= new FingerprintTemplate(candidates.getBytes("template"));
							FingerprintMatcher identity_matcher = new FingerprintMatcher(identity_probe);
							scores.add(i, identity_matcher.match(identity_candidate));
							System.out.println("Similarity score: " + scores.get(i) );

							i++;
						}
						} catch (SQLException e) {
							e.printStackTrace();
						}
						//index identifier
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
						System.out.println("The user is : " + identify_first.get(index) + " " + identify_last.get(index));
					break;
			default: break;

			}
		}while(mode != 0);
	}

	public static void imgWindow(File file) throws IOException{
			//close the window if already open
			if(frame != null){
				frame.dispose();
			}
			//create an image var
			Image img = ImageIO.read(file);
			//create a jframe window and display the image
				//frame is a static var in order to close window when new image is created
			frame = new JFrame(null, null);
			JLabel label = new JLabel(new ImageIcon(img));
			frame.add(label);
			frame.pack();
			frame.setVisible(true);
	}

	/**HELPER METHODS**/
	public static void enrollQuery(String fname, String lname, byte[] data) {
		try {
			//int result;
			Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/fp5","root","");
			PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (fname, lname, template) VALUES (?, ?, ?)");
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
	public static ResultSet retrieveQuery(String fname, String lname) {
		ResultSet result = null;
		try {
			Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/fp5","root","");
			PreparedStatement stmt = conn.prepareStatement("SELECT template FROM users WHERE fname = ? and lname = ?");
		    stmt.setString(1, fname);
		    stmt.setString(2, lname);
		    result = stmt.executeQuery();
		    System.out.println("Verification Data Successfully Retrieved.");
	    }catch (SQLException e) {
	    	System.out.println("Error retrieving user: " + e.getMessage());
	    }
		return result;
	  }

	public static ResultSet identifyQuery() {
		ResultSet result = null;
		try {
			Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/fp5","root","");
			PreparedStatement stmt = conn.prepareStatement("SELECT user_id, fname, lname, template FROM users");
		    result = stmt.executeQuery();
		    System.out.println("Identification Data Successfully Retrieved.");
	    }catch (SQLException e) {
	    	System.out.println("Error retrieving user(s): " + e.getMessage());
	    }
		return result;
	}

	public static void printScanMessage() {
		System.out.print("Scanning print");
		try {
			Thread.sleep(250);
			System.out.print(".");
			Thread.sleep(250);
			System.out.print(".");
			Thread.sleep(250);
			System.out.println(".");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}


