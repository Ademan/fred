package freenet.node.simulator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import freenet.client.ClientMetadata;
import freenet.client.FetchException;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertBlock;
import freenet.client.InsertException;
import freenet.crypt.RandomSource;
import freenet.keys.FreenetURI;
import freenet.node.Node;
import freenet.node.NodeStarter;
import freenet.node.Version;
import freenet.support.Fields;
import freenet.support.Logger;
import freenet.support.PooledExecutor;
import freenet.support.api.Bucket;
import freenet.support.io.Closer;
import freenet.support.io.FileUtil;

/** Simulates MHKs. Creates 4 CHKs, inserts the first one 3 times, and inserts the
 * others 1 time each. Pulls them all after 1, 3, 7, 15, 31 etc days and computes 
 * success rates for the 1 versus the 3 combined. I am convinced the 3 combined will
 * be much more successful, but evanbd isn't.
 * @author Matthew Toseland <toad@amphibian.dyndns.org> (0xE43DA450)
 *
 */
public class LongTermMHKTest {
	
	private static final int TEST_SIZE = 64 * 1024;

	private static final int EXIT_NO_SEEDNODES = 257;
	private static final int EXIT_FAILED_TARGET = 258;
	private static final int EXIT_THREW_SOMETHING = 261;
	private static final int EXIT_DIFFERENT_URI = 262;

	private static final int DARKNET_PORT1 = 5010;
	private static final int OPENNET_PORT1 = 5011;
	private static final int DARKNET_PORT2 = 5012;
	private static final int OPENNET_PORT2 = 5013;
	
	/** Delta - the number of days we wait before fetching. */
	private static final int DELTA = 7;

	private static final DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd", Locale.US);
	private static final GregorianCalendar today = (GregorianCalendar) Calendar.getInstance(TimeZone.getTimeZone("GMT"));

	public static void main(String[] args) {
		if (args.length < 1 || args.length > 2) {
			System.err.println("Usage: java freenet.node.simulator.LongTermPushPullTest <unique identifier>");
			System.exit(1);
		}
		String uid = args[0];
		
		List<String> csvLine = new ArrayList<String>();
		System.out.println("DATE:" + dateFormat.format(today.getTime()));
		csvLine.add(dateFormat.format(today.getTime()));

		System.out.println("Version:" + Version.buildNumber());
		csvLine.add(String.valueOf(Version.buildNumber()));

		int exitCode = 0;
		Node node = null;
		Node node2 = null;
		FileInputStream fis = null;
		File file = new File("mhk-test-"+uid + ".csv");
		long t1, t2;
		
		try {
			
			// INSERT STUFF
			
			final File dir = new File("longterm-mhk-test-" + uid);
			FileUtil.removeAll(dir);
			RandomSource random = NodeStarter.globalTestInit(dir.getPath(), false, Logger.ERROR, "", false);
			File seednodes = new File("seednodes.fref");
			if (!seednodes.exists() || seednodes.length() == 0 || !seednodes.canRead()) {
				System.err.println("Unable to read seednodes.fref, it doesn't exist, or is empty");
				System.exit(EXIT_NO_SEEDNODES);
			}

			final File innerDir = new File(dir, Integer.toString(DARKNET_PORT1));
			innerDir.mkdir();
			fis = new FileInputStream(seednodes);
			FileUtil.writeTo(fis, new File(innerDir, "seednodes.fref"));
			fis.close();

			// Create one node
			node = NodeStarter.createTestNode(DARKNET_PORT1, OPENNET_PORT1, dir.getPath(), false, Node.DEFAULT_MAX_HTL,
			        0, random, new PooledExecutor(), 1000, 4 * 1024 * 1024, true, true, true, true, true, true, true,
			        12 * 1024, true, true, false, null);
			Logger.getChain().setThreshold(Logger.ERROR);

			// Start it
			node.start(true);
			t1 = System.currentTimeMillis();
			if (!TestUtil.waitForNodes(node)) {
				exitCode = EXIT_FAILED_TARGET;
				return;
			}
				
			t2 = System.currentTimeMillis();
			System.out.println("SEED-TIME:" + (t2 - t1));
			csvLine.add(String.valueOf(t2 - t1));

			// Create four CHKs
			
			Bucket single = randomData(node);
			Bucket[] mhks = new Bucket[3];
			
			for(int i=0;i<mhks.length;i++) mhks[i] = randomData(node);
			
			HighLevelSimpleClient client = node.clientCore.makeClient((short) 0);

			System.err.println("Inserting single block 3 times");
			
			InsertBlock block = new InsertBlock(single, new ClientMetadata(), FreenetURI.EMPTY_CHK_URI);
			
			FreenetURI uri = null;
			
			int successes = 0;
			
			for(int i=0;i<3;i++) {
				System.err.println("Inserting single block, try #"+i);
				try {
					t1 = System.currentTimeMillis();
					FreenetURI thisURI = client.insert(block, false, null);
					if(uri != null && !thisURI.equals(uri)) {
						System.err.println("URI "+i+" is "+thisURI+" but previous is "+uri);
						System.exit(EXIT_DIFFERENT_URI);
					}
					uri = thisURI;
					t2 = System.currentTimeMillis();
					
					System.out.println("PUSH-TIME-" + i + ":" + (t2 - t1)+" for "+uri+" for single block");
					csvLine.add(String.valueOf(t2 - t1));
					csvLine.add(uri.toASCIIString());
					successes++;
				} catch (InsertException e) {
					e.printStackTrace();
					csvLine.add(FetchException.getShortMessage(e.getMode()));
					csvLine.add("N/A");
					System.out.println("INSERT FAILED: "+e+" for insert "+i+" for single block");
				}
			}
			
			if(successes == 3)
				System.err.println("All inserts succeeded for single block: "+successes);
			else if(successes != 0)
				System.err.println("Some inserts succeeded for single block: "+successes);
			else
				System.err.println("NO INSERTS SUCCEEDED FOR SINGLE BLOCK: "+successes);
			
			uri = null;
			
			// Insert 3 blocks
			
			for(int i=0;i<3;i++) {
				System.err.println("Inserting MHK #"+i);
				uri = null;
				block = new InsertBlock(mhks[i], new ClientMetadata(), FreenetURI.EMPTY_CHK_URI);
				try {
					t1 = System.currentTimeMillis();
					FreenetURI thisURI = client.insert(block, false, null);
					uri = thisURI;
					t2 = System.currentTimeMillis();
					
					System.out.println("PUSH-TIME-" + i + ":" + (t2 - t1)+" for "+uri+" for MHK #"+i);
					csvLine.add(String.valueOf(t2 - t1));
					csvLine.add(uri.toASCIIString());
					successes++;
				} catch (InsertException e) {
					e.printStackTrace();
					csvLine.add(FetchException.getShortMessage(e.getMode()));
					csvLine.add("N/A");
					System.out.println("INSERT FAILED: "+e+" for MHK #"+i);
				}
			}
			
			if(successes == 3)
				System.err.println("All inserts succeeded for MHK: "+successes);
			else if(successes != 0)
				System.err.println("Some inserts succeeded for MHK: "+successes);
			else
				System.err.println("NO INSERTS SUCCEEDED FOR MHK: "+successes);
			
			uri = null;
			
			// PARSE FILE AND FETCH OLD STUFF IF APPROPRIATE
			
			boolean match = false;
			
			FreenetURI singleURI = null;
			FreenetURI[] mhkURIs = new FreenetURI[3];
			fis = new FileInputStream(file);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis));
			String line = null;
			int total = 0, singleKeysSucceeded = 0, mhkSucceeded = 0;
			while((line = br.readLine()) != null) {
				
				singleURI = null;
				for(int i=0;i<mhkURIs.length;i++) mhkURIs[i] = null;
				//System.out.println("LINE: "+line);
				String[] split = line.split("!");
				Date date = dateFormat.parse(split[0]);
				GregorianCalendar calendar = new GregorianCalendar();
				calendar.setTime(date);
				System.out.println("Date: "+dateFormat.format(calendar.getTime()));
				GregorianCalendar target = (GregorianCalendar) today.clone();
				target.set(Calendar.HOUR_OF_DAY, 0);
				target.set(Calendar.MINUTE, 0);
				target.set(Calendar.MILLISECOND, 0);
				target.set(Calendar.SECOND, 0);
				target.add(Calendar.DAY_OF_MONTH, -DELTA);
				calendar.set(Calendar.HOUR_OF_DAY, 0);
				calendar.set(Calendar.MINUTE, 0);
				calendar.set(Calendar.MILLISECOND, 0);
				calendar.set(Calendar.SECOND, 0);
				calendar.getTime();
				target.getTime();
				try {
					if(split.length < 3) continue;
					int seedTime = Integer.parseInt(split[2]);
					System.out.println("Seed time: "+seedTime);
					
					int token = 3;
					
					for(int i=0;i<3;i++) {
						int insertTime = Integer.parseInt(split[token]);
						System.out.println("Single key insert "+i+" : "+insertTime);
						token++;
						FreenetURI thisURI = new FreenetURI(split[token]);
						if(singleURI == null)
							singleURI = thisURI;
						else {
							if(!singleURI.equals(thisURI)) {
								System.err.println("URI is not the same for all 3 inserts: was "+singleURI+" but "+i+" is "+thisURI);
								continue;
							}
						}
						token++;
					}
					System.out.println("Single key URI: "+singleURI);
					
					for(int i=0;i<3;i++) {
						int insertTime = Integer.parseInt(split[token]);
						token++;
						mhkURIs[i] = new FreenetURI(split[token]);
						token++;
						System.out.println("MHK #"+i+" URI: "+mhkURIs[i]+" insert time "+insertTime);
					}
					
				} catch (NumberFormatException e) {
					System.err.println("Failed to parse row: "+e);
					continue;
				} catch (MalformedURLException e) {
					System.err.println("Failed to parse row: "+e);
					continue;
				}
				if(target.getTimeInMillis() == calendar.getTimeInMillis()) {
					System.out.println("Found row for target date "+dateFormat.format(target.getTime())+" : "+dateFormat.format(calendar.getTime()));
					System.out.println("Version: "+split[1]);
					match = true;
					break;
				} else if(split.length > 3+6+6) {
					int token = 3 + 6 + 6;
					int singleKeyFetchTime = -1;
					boolean singleKeySuccess = false;
					for(int i=0;i<3;i++) {
						// Fetched 3 times
						if(!singleKeySuccess) {
							try {
								singleKeyFetchTime = Integer.parseInt(split[token]);
								singleKeySuccess = true;
								System.out.println("Fetched single key on try "+i+" on "+date+" in "+singleKeyFetchTime+"ms");
							} catch (NumberFormatException e) {
								System.out.println("Failed fetch single key on "+date+" try "+i+" : "+split[token]);
								singleKeyFetchTime = -1;
							}
						} // Else will be empty.
						token++;
					}
					boolean mhkSuccess = false;
					for(int i=0;i<3;i++) {
						int mhkFetchTime = -1;
						try {
							mhkFetchTime = Integer.parseInt(split[token]);
							mhkSuccess = true;
							System.out.println("Fetched MHK #"+i+" on "+date+" in "+mhkFetchTime+"ms");
						} catch (NumberFormatException e) {
							System.out.println("Failed fetch MHK #"+i+" on "+date+" : "+split[token]);
						}
						token++;
					}
					total++;
					if(singleKeySuccess)
						singleKeysSucceeded++;
					if(mhkSuccess)
						mhkSucceeded++;
				}
			}
			System.out.println("Total attempts: "+total);
			System.out.println("Single keys succeeded: "+singleKeysSucceeded);
			System.out.println("MHKs succeeded: "+mhkSucceeded);
			fis.close();
			fis = null;
			
			// FETCH STUFF
			
			
			if(match) {
				
				// FETCH SINGLE URI
				
				// Fetch the first one 3 times, since the MHK is 3 fetches also.
				// Technically this is 9 fetches because we multiply by 3 fetches per high-level fetch by default.
				
				boolean fetched = false;
				for(int i=0;i<3;i++) {
					if(fetched) {
						csvLine.add("");
						continue;
					}
					try {
						t1 = System.currentTimeMillis();
						client.fetch(singleURI);
						t2 = System.currentTimeMillis();
						
						System.out.println("PULL-TIME FOR SINGLE URI:" + (t2 - t1));
						csvLine.add(String.valueOf(t2 - t1));
						fetched = true;
					} catch (FetchException e) {
						if (e.getMode() != FetchException.ALL_DATA_NOT_FOUND
								&& e.getMode() != FetchException.DATA_NOT_FOUND)
							e.printStackTrace();
						csvLine.add(FetchException.getShortMessage(e.getMode()));
						System.err.println("FAILED PULL FOR SINGLE URI: "+e);
					}
				}
				
				for(int i=0;i<mhkURIs.length;i++) {
					try {
						t1 = System.currentTimeMillis();
						client.fetch(mhkURIs[i]);
						t2 = System.currentTimeMillis();
						
						System.out.println("PULL-TIME FOR MHK #"+i+":" + (t2 - t1));
						csvLine.add(String.valueOf(t2 - t1));
					} catch (FetchException e) {
						if (e.getMode() != FetchException.ALL_DATA_NOT_FOUND
								&& e.getMode() != FetchException.DATA_NOT_FOUND)
							e.printStackTrace();
						csvLine.add(FetchException.getShortMessage(e.getMode()));
						System.err.println("FAILED PULL FOR MHK #"+i+": "+e);
					}
				}
			}
			
		} catch (Throwable t) {
			t.printStackTrace();
			exitCode = EXIT_THREW_SOMETHING;
		} finally {
			try {
				if (node != null)
					node.park();
			} catch (Throwable tt) {
			}
			try {
				if (node2 != null)
					node2.park();
			} catch (Throwable tt) {
			}
			Closer.close(fis);

			try {
				FileOutputStream fos = new FileOutputStream(file, true);
				PrintStream ps = new PrintStream(fos);

				ps.println(Fields.commaList(csvLine.toArray(), '!'));

				ps.close();
				fos.close();
			} catch (IOException e) {
				e.printStackTrace();
				exitCode = EXIT_THREW_SOMETHING;
			}
			
			System.exit(exitCode);
		}
	}	
	
	private static Bucket randomData(Node node) throws IOException {
		Bucket data = node.clientCore.tempBucketFactory.makeBucket(TEST_SIZE);
		OutputStream os = data.getOutputStream();
		byte[] buf = new byte[4096];
		for (long written = 0; written < TEST_SIZE;) {
			node.fastWeakRandom.nextBytes(buf);
			int toWrite = (int) Math.min(TEST_SIZE - written, buf.length);
			os.write(buf, 0, toWrite);
			written += toWrite;
		}
		os.close();
		return data;
	}

}
