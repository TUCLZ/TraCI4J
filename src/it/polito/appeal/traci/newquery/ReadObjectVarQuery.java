/*   
    Copyright (C) 2011 ApPeAL Group, Politecnico di Torino

    This file is part of TraCI4J.

    TraCI4J is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    TraCI4J is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with TraCI4J.  If not, see <http://www.gnu.org/licenses/>.
*/

package it.polito.appeal.traci.newquery;

import it.polito.appeal.traci.TraCIException;
import it.polito.appeal.traci.protocol.Command;
import it.polito.appeal.traci.protocol.Constants;
import it.polito.appeal.traci.protocol.ResponseContainer;
import it.polito.appeal.traci.protocol.StatusResponse;
import it.polito.appeal.traci.protocol.StringList;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import de.uniluebeck.itm.tcpip.Storage;

abstract class ReadObjectVarQuery<V> extends ValueReadQuery<V> {

	private final int commandID;
	private final String objectID;
	private final int varID;
	
	ReadObjectVarQuery(DataInputStream dis, DataOutputStream dos, int commandID, String objectID, int varID) {
		super(dis, dos);
		this.commandID = commandID;
		this.objectID = objectID;
		this.varID = varID;
	}
	
	@Override
	List<Command> getRequests() {
		Command cmd = new Command(commandID);
		Storage content = cmd.content();
		content.writeByte(varID);
		content.writeStringASCII(objectID);
		return Collections.singletonList(cmd);
	}

	@Override
	void pickResponses(Iterator<ResponseContainer> responseIterator) throws TraCIException {
		ResponseContainer respc = responseIterator.next();
		StatusResponse statusResp = respc.getStatus();
		if (statusResp.id() != commandID)
			throw new TraCIException("command and status IDs must match");
		if (statusResp.result() != Constants.RTYPE_OK)
			throw new TraCIException("SUMO error for command "
					+ statusResp.id() + ": " + statusResp.description());

		Command resp = respc.getResponse();
		if (resp.content().readUnsignedByte() != varID)
			throw new TraCIException("variable ID mismatch");
		if (!resp.content().readStringASCII().equals(objectID))
			throw new TraCIException("object ID mismatch");
		
		V value = readValue(resp);
		setDone(value);
	}
	
	protected abstract V readValue(Command resp) throws TraCIException; 

	public static class IntegerQ extends ReadObjectVarQuery<Integer> {

		IntegerQ(DataInputStream dis, DataOutputStream dos, int commandID,
				String objectID, int varID) {
			super(dis, dos, commandID, objectID, varID);
		}

		@Override
		protected Integer readValue(Command resp)
				throws TraCIException {
			Storage content = resp.content();
			if ((int)content.readByte() != Constants.TYPE_INTEGER)
				throw new TraCIException("integer type ID expected");
			return content.readInt();
		}
		
	}
	
	public static class DoubleQ extends ReadObjectVarQuery<Double> {

		DoubleQ(DataInputStream dis, DataOutputStream dos, int commandID, String objectID, int varID) {
			super(dis, dos, commandID, objectID, varID);
		}

		@Override
		protected Double readValue(Command resp) throws TraCIException {
			Storage content = resp.content();
			if ((int)content.readByte() != Constants.TYPE_DOUBLE)
				throw new TraCIException("double type ID expected");
			return content.readDouble();
		}
	}


	public static class PositionQ extends ReadObjectVarQuery<Point2D> {

		PositionQ(DataInputStream dis, DataOutputStream dos, int commandID, String vehicleID, int varID) {
			super(dis, dos, commandID, vehicleID, varID);
		}

		@Override
		protected Point2D readValue(Command resp) throws TraCIException {
			Storage content = resp.content();
			double x = content.readDouble();
			double y = content.readDouble();
			return new Point2D.Double(x, y);
		}
	}

	public static class StringQ extends ReadObjectVarQuery<String> {
		
		StringQ(DataInputStream dis, DataOutputStream dos, int commandID, String objectID, int varID) {
			super(dis, dos, commandID, objectID, varID);
		}
		
		protected String readValue(Command resp) throws TraCIException {
			Storage content = resp.content();
			if ((int)content.readByte() != Constants.TYPE_STRING)
				throw new TraCIException("string type ID expected");
			return content.readStringASCII();
		}
	}

	public static class StringListQ extends ReadObjectVarQuery<List<String>> {

		StringListQ(DataInputStream dis, DataOutputStream dos, int commandID,
				String objectID, int varID) {
			super(dis, dos, commandID, objectID, varID);
		}

		@Override
		protected List<String> readValue(
				Command resp) throws TraCIException {
			return new StringList(resp.content(), true);
		}
	}
	
	public static class BoundingBoxQ extends ReadObjectVarQuery<Rectangle2D> {

		BoundingBoxQ(DataInputStream dis, DataOutputStream dos, int commandID, String vehicleID, int varID) {
			super(dis, dos, commandID, vehicleID, varID);
		}

		@Override
		protected Rectangle2D readValue(Command resp) throws TraCIException {
			Storage content = resp.content();
			return new it.polito.appeal.traci.protocol.BoundingBox(content, true);
		}
	}
	
	public static class TraciObjectQ<V extends TraciObject<?>> extends ReadObjectVarQuery<V> {

		private final Repository<V> repo;
		
		TraciObjectQ(DataInputStream dis, DataOutputStream dos,
				int commandID, String objectID, int varID, Repository<V> repo) {
			super(dis, dos, commandID, objectID, varID);
			this.repo = repo;
		}

		@Override
		protected V readValue(Command resp) throws TraCIException {
			Storage content = resp.content();
			if ((int)content.readByte() != Constants.TYPE_STRING)
				throw new TraCIException("string type ID expected");
			String id = content.readStringASCII();
			try {
				return repo.getByID(id);
			} catch (IOException e) {
				throw new TraCIException(e.toString());
			}
		}
	}
	
	public static class EdgeQ extends TraciObjectQ<Edge> {
		EdgeQ(DataInputStream dis, DataOutputStream dos, int commandID,
				String objectID, int varID, Repository<Edge> repo) {
			super(dis, dos, commandID, objectID, varID, repo);
		}
	}
	
	public static class LaneQ extends TraciObjectQ<Lane> {
		LaneQ(DataInputStream dis, DataOutputStream dos, int commandID,
				String objectID, int varID, Repository<Lane> repo) {
			super(dis, dos, commandID, objectID, varID, repo);
		}
	}

}