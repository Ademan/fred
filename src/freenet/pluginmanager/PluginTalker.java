/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.pluginmanager;

import freenet.node.Node;
import freenet.node.fcp.FCPConnectionHandler;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

/**
 * @author saces
 * 
 */
public class PluginTalker {

	Node node;
	private PluginReplySender replysender;

	private Boolean access;

	FredPluginFCP plugin;

	PluginTalker(FredPluginTalker fpt, Node node2, String pluginname2, String identifier2) throws PluginNotFoundException {
		node = node2;
		plugin = findPlugin(pluginname2);
		access = null;
		replysender = new PluginReplySenderDirect(node2, fpt, pluginname2, identifier2);
	}

	public PluginTalker(Node node2, FCPConnectionHandler handler, String pluginname2, String identifier2, boolean access2) throws PluginNotFoundException {
		node = node2;
		plugin = findPlugin(pluginname2);
		access = new Boolean(access2);
		replysender = new PluginReplySenderFCP(handler, pluginname2, identifier2);
	}
	
	private FredPluginFCP findPlugin(String pluginname2) throws PluginNotFoundException {

		Logger.normal(this, "Searching fcp plugin: " + pluginname2);
		FredPluginFCP plug = node.pluginManager.getFCPPlugin(pluginname2);
		if (plug == null) {
			Logger.error(this, "Could not find fcp plugin: " + pluginname2);
			throw new PluginNotFoundException();
		}
		Logger.normal(this, "Found fcp plugin: " + pluginname2);
		return plug;

	}


	public void send(final SimpleFieldSet plugparams, final Bucket data2) {

		node.executor.execute(new Runnable() {

			public void run() {

				try {
					plugin.handle(replysender, plugparams, data2, access);
				} catch (Throwable t) {
					Logger.error(this, "Cought error while execute fcp plugin handler" + t.getMessage(), t);
				}

			}
		}, "FCPPlugin talk runner for " + this);

	}
}
