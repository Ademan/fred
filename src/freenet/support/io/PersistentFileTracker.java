/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.support.io;

import java.io.File;

public interface PersistentFileTracker extends DiskSpaceChecker {

	public void register(File file);

	/** Notify that we have finished with a bucket and it should be freed after the
	 * next serialization to disk.
	 * @param bucket The bucket to free.
	 */
	public void delayedFree(DelayedFree bucket);

	/**
	 * Get the persistent temp files directory.
	 */
	public File getDir();

	public FilenameGenerator getGenerator();
	
}
