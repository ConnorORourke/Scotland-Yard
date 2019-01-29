package uk.ac.bris.cs.scotlandyard.model;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.Black;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.Double;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.Secret;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;

public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move>, MoveVisitor{

	private final List<Boolean> rounds;
	private final Graph<Integer, Transport> graph;
	private final ArrayList<ScotlandYardPlayer> playerList;
	private int currentRound;
	private int currentPlayer;
	private final ArrayList<Spectator> spectators;
	private int mrXLastLocation;

//Constructor
	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
			PlayerConfiguration mrX, PlayerConfiguration firstDetective,
			PlayerConfiguration... restOfTheDetectives){
		//Set current round to NOT_STARTED
		currentRound = NOT_STARTED;
		//Set current player to 0 (MrX)
		currentPlayer = 0;
		//Set MrX's last location to 0
		mrXLastLocation = 0;
		//Create the list for the players
		playerList = new ArrayList<ScotlandYardPlayer>();
		//Create the list for the spectators
		spectators = new ArrayList<Spectator>();
		//requireNonNull() to ensure they are not null which could break things
		this.rounds = requireNonNull(rounds);
		this.graph = requireNonNull(graph);
		//Check that rounds and graph aren't empty
		if(rounds.isEmpty()){throw new IllegalArgumentException("Empty rounds");}
		if(graph.isEmpty()){throw new IllegalArgumentException("Empty graph");}
		//Check that MrX has the black colour
		if(mrX.colour != Black){throw new IllegalArgumentException("MrX should be Black");}
		//Checks whether any player is null, adds them to configurations
		ArrayList<PlayerConfiguration> configurations = new ArrayList<PlayerConfiguration>();
    for (PlayerConfiguration configuration : restOfTheDetectives){
			configurations.add(requireNonNull(configuration));
		}
    configurations.add(0, requireNonNull(firstDetective));
    configurations.add(0, requireNonNull(mrX));
		//Loop to check whether there are duplicate locations or colours
		Set<Integer> locSet = new HashSet<Integer>();
		Set<Colour> colSet = new HashSet<Colour>();
		for (PlayerConfiguration configuration : configurations){
	    if(locSet.contains(configuration.location)){
		    throw new IllegalArgumentException("Duplicate location");
			}
			if(colSet.contains(configuration.colour)){
		    throw new IllegalArgumentException("Duplicate colour");
			}
	    locSet.add(configuration.location);
			colSet.add(configuration.colour);
		}
		//Make sure MrX and the detectives have the correct tickets (contains a mapping to each ticket)
		ArrayList<Ticket> allTickets = new ArrayList<Ticket>(asList(Ticket.Bus,Ticket.Taxi,Ticket.Underground,
																									 Ticket.Double,Ticket.Secret));
		for (Ticket ticket : allTickets){
			for (PlayerConfiguration configuration : configurations){
				if(!(configuration.tickets.containsKey(ticket))){throw new IllegalArgumentException("A player is missing a ticket");}
				//If statement to check whether detectives (!Black) have Double or Secret tickets
				if((configuration.colour != Black)&&((ticket == Ticket.Double)||(ticket == Ticket.Secret))){
					if(!(configuration.tickets.getOrDefault(ticket, 0) == 0)){
						throw new IllegalArgumentException("A detective should not have Double or Secret tickets");
					}
				}
			}
		}
		//Add the players to the playerList
		for(PlayerConfiguration c : configurations){
			playerList.add(new ScotlandYardPlayer(c.player, c.colour, c.location, c.tickets));
		}
	}

	//Returns Mr X
	private ScotlandYardPlayer mrX(){
		return playerList.get(0);
	}

	//Returns a list of the detectives
	private ArrayList<ScotlandYardPlayer> detectives(){
		ArrayList<ScotlandYardPlayer> detectives = new ArrayList<>();
		for (int i = 1; i < playerList.size(); i++){
			detectives.add(playerList.get(i));
		}
		return detectives;
	}

	@Override
	public void startRotate(){
		//Gets the current ScotlandYardPlayer player
		ScotlandYardPlayer startPlayer = getCurrentScotlandYardPlayer();
		//Creates a set of valid moves which can be made
		Set<Move> validMoves = validMoves(startPlayer);
		if(!isGameOver()){
			//Calls the makeMove() method on the player attribute of the current ScotlandYardPlayer
			startPlayer.player().makeMove(this, startPlayer.location(), validMoves, this);
		}
		else {
			notifyGameOver();
			throw new IllegalStateException("The game is already over!");
		}
	}

	@Override
	//Check whether the move chosen by the player is valid
	public void accept(Move move){
		Set<Move> validMoves = validMoves(getCurrentScotlandYardPlayer());
		//Ensure the move is not null
		move = requireNonNull(move);
		//Checks whether the argument move is valid
		if(validMoves.contains(move)){
			//Uses dynamic dispatch and the visitor design pattern to match to the correct ticket
			move.visit(this);
			//If all players have moved, end of rotation
			if(currentPlayer == playerList.size()-1){
				if(isGameOver()){
					notifyGameOver();

				}
				else {
					currentPlayer = 0;
				  notifyRotationComplete();
				}
			}
			else {
				if(isGameOver()){
					notifyGameOver();

				}
				else {
					//Increment the currentPlayer
					currentPlayer++;
					//If the round is not over, call makeMove on the next player
					ScotlandYardPlayer nextPlayer = getCurrentScotlandYardPlayer();
					nextPlayer.player().makeMove(this, nextPlayer.location(), validMoves(nextPlayer), this);
				}
			}
		}
		else {
			throw new IllegalArgumentException("The move was not valid!");
		}
	}

	//A helper method to move the player and remove tickets
	public void updatePlayer(ScotlandYardPlayer player, int destination, Ticket ticket){
		//Moves the player to the destination of their move
		player.location(destination);
		//Removes the ticket that was used
		player.removeTicket(ticket);
		//Gives MrX the ticket the detective used
		if(player.isDetective()){
			mrX().addTicket(ticket);
		}
	}

	//A visit method for PassMove moves
	@Override
	public void visit(PassMove move){
		//Get the ScotlandYardPlayer that just took a move
		ScotlandYardPlayer player = getCurrentScotlandYardPlayer();
		if(player.isMrX()){
			throw new IllegalStateException("MrX cannot make PassMoves!");
		}
		HashSet<Move> set = new HashSet<Move>();
		set.add(new PassMove(player.colour()));
		//Throw exception if there were valid moves (not just a PassMove)
		if(!validMoves(player).equals(set)){
			throw new IllegalArgumentException("There were valid moves to make!");
		}
		//Notify spectators
		notifyMoveMade(move);
	}

	//A visit method for TicketMove moves
	@Override
	public void visit(TicketMove move){
		//Get the ScotlandYardPlayer that just took a move
		ScotlandYardPlayer player = getCurrentScotlandYardPlayer();
		updatePlayer(player, move.destination(), move.ticket());
		//If MrX has just moved, increment the round and notify spectators
		if(player.isMrX()){
			//If it is a reveal round, update MrX's last known location
			if(isRevealRound()){
				mrXLastLocation = move.destination();
			}
			else {
				move = new TicketMove(move.colour(), move.ticket(), mrXLastLocation);
			}
			currentRound++;
			notifyRoundStarted();
		}
		//Notify spectators
		notifyMoveMade(move);
	}

	//A visit method for DoubleMove moves
	@Override
	public void visit(DoubleMove move){
		//Get the ScotlandYardPlayer that just took a move
		ScotlandYardPlayer player = getCurrentScotlandYardPlayer();
		TicketMove firstMove;
		TicketMove secondMove;
		DoubleMove doubleMove;
		//Make a hidden version of the first move on hidden rounds
		if(!isRevealRound()){
		  firstMove = new TicketMove(player.colour(), move.firstMove().ticket(), mrXLastLocation);
    }
		//Get the correct firstMove if it's a reveal round
		else {
			firstMove = move.firstMove();
			mrXLastLocation = firstMove.destination();
		}
		//Make a hidden version of the second move on hidden rounds
		if(!(rounds.get(currentRound + 1))){
			secondMove = new TicketMove(player.colour(), move.secondMove().ticket(), mrXLastLocation);
		}
		//Get the correct secondMove if it's a reveal round
		else {
			secondMove = move.secondMove();
			mrXLastLocation = secondMove.destination();
		}
		doubleMove = new DoubleMove(player.colour(), firstMove, secondMove);
		//Nofify spectators of the double move
		notifyMoveMade(doubleMove);
		player.removeTicket(Ticket.Double);
		updatePlayer(player, move.firstMove().destination(), move.firstMove().ticket());
		updatePlayer(player, move.secondMove().destination(), move.secondMove().ticket());
		//Increment the round counter and notify spectators
		currentRound++;
		notifyRoundStarted();
		notifyMoveMade(firstMove);
		currentRound++;
		notifyRoundStarted();
		notifyMoveMade(secondMove);
	}

	//Method to create a set of valid moves
	private Set<Move> validMoves(ScotlandYardPlayer player){
		//Creates an empty set for putting moves in and returning them
		HashSet<Move> set = new HashSet<Move>();
		//Creates an empty array for putting single moves in
		List<TicketMove> singleMoves = new ArrayList<TicketMove>();
		//Gets a list of the edges from the current node
		List<Edge<Integer, Transport>> edgesFrom = new ArrayList<Edge<Integer, Transport>>(graph.getEdgesFrom(graph.getNode(player.location())));

		//--------------------------SINGLE MOVE--------------------------//

		//Gets a list of the possible single moves
		singleMoves = movesFrom(edgesFrom, player);
		//Add the moves from the list to the set to return
		for (TicketMove move : singleMoves){
			set.add(move);
		}

		//--------------------------DOUBLE MOVE--------------------------//

		List<DoubleMove> doubleMoves = new ArrayList<DoubleMove>();
		//For every possible first move, if the player has a double ticket, get the possible double moves
		if(player.hasTickets(Ticket.Double) && currentRound < rounds.size() - 1){
			for (TicketMove firstMove : singleMoves){
				//Get the nodes reached by the first move
				edgesFrom = new ArrayList<Edge<Integer, Transport>>(graph.getEdgesFrom(graph.getNode(firstMove.destination())));
				//Take the ticket used in the firstMove from the player
				player.removeTicket(firstMove.ticket());
				//Get the moves playable as a second move
				List<TicketMove> secondMoves = movesFrom(edgesFrom, player);
				//Add each double move possible
				for (TicketMove secondMove : secondMoves){
					set.add(new DoubleMove(player.colour(), firstMove, secondMove));
				}
				player.addTicket(firstMove.ticket());
			}
		}
		if(set.isEmpty() && player.isDetective()){
			set.add(new PassMove(player.colour()));
		}
		return set;
	}

	private List<TicketMove> movesFrom(List<Edge<Integer, Transport>> edgesFrom, ScotlandYardPlayer player){
		List<TicketMove> moves = new ArrayList<TicketMove>();
		//Iterates through the edges and adds the nodes that can be traversed to with a ticket
		for (Edge<Integer, Transport> edge : edgesFrom){
			//The ticket version of the transport
			Ticket currentTicket = Ticket.fromTransport(edge.data());
			//Get the list of occupied locations
			ArrayList<Integer> locationList = occupiedLocations();
			//If the edge can be traversed and is not occupied, add the TicketMove to the set of Moves
			if(player.hasTickets(currentTicket) || player.hasTickets(Ticket.Secret)){
				if(!locationList.contains(edge.destination().value())){
					//If the player has a valid transport ticket to get to the location add it
					if(player.hasTickets(currentTicket)){
						moves.add(new TicketMove(player.colour(), currentTicket, edge.destination().value()));
					}
					//If the player has a secret ticket to get to the location add it
					if(player.hasTickets(Ticket.Secret)){
						moves.add(new TicketMove(player.colour(), Ticket.Secret, edge.destination().value()));
					}
				}
			}
		}
		return moves;
	}

	//A helper method to return an ArrayList of the occupied locations on the board
	private ArrayList<Integer> occupiedLocations(){
		//Create list of occupied locations except the current player and Mr X's location
		ArrayList<Integer> locationList = new ArrayList<Integer>();
		for (ScotlandYardPlayer locPlayer : playerList){
			if((locPlayer != getCurrentScotlandYardPlayer())&&(!locPlayer.isMrX())){
				locationList.add(locPlayer.location());
			}
		}
		return locationList;
	}

	//A helper method to notify all the spectators that the game is over
	private void notifyGameOver(){
		Set<Colour> winningPlayers = getWinningPlayers();
		for (Spectator spectator : spectators){
			spectator.onGameOver(this, winningPlayers);
		}
	}

	//A helper method to notify all the spectators that a round has started
	private void notifyRoundStarted(){
		for (Spectator spectator : spectators){
			spectator.onRoundStarted(this, currentRound);
		}
	}

	//A helper method to notify all the spectators that a move has been made
	private void notifyMoveMade(Move move){
		for (Spectator spectator : spectators){
			spectator.onMoveMade(this, move);
		}
	}

	//A helper method to notify all the spectators that a round has ended
	private void notifyRotationComplete(){
		for (Spectator spectator : spectators){
			spectator.onRotationComplete(this);
		}
	}

	@Override
	public void registerSpectator(Spectator spectator){
		//Throws an execption if the spectator has already been added
		if(spectators.contains(spectator)){
			throw new IllegalArgumentException("This spectator has already been added!");
		}
		//Adds the spectator to the list of spectators, making sure it's not null
		spectators.add(requireNonNull(spectator));
	}

	@Override
	public void unregisterSpectator(Spectator spectator){
		spectator = requireNonNull(spectator);
		//Throws an exception if the spectator doesn't exist
		if(!(spectators.contains(spectator))){
			throw new IllegalArgumentException("The given spectator has not been added!");
		}
		//Removes the spectator from the list of spectators
		spectators.remove(spectator);
	}

	@Override
	public Collection<Spectator> getSpectators(){
		//Returns an immutable copy of the list of spectators
		return Collections.unmodifiableList(spectators);
	}

	@Override
	public List<Colour> getPlayers(){
		//Make & return a new unmodifiable list of colours corresponding to the players
		List<Colour> colourList = new ArrayList<Colour>();
		for(ScotlandYardPlayer player : playerList){
			colourList.add(player.colour());
		}
		return Collections.unmodifiableList(colourList);
	}


	@Override
	public int getPlayerLocation(Colour colour){
		//If the player is MrX and the round is not a reveal round, return 0
		if(findPlayer(colour).isMrX()){
			return mrXLastLocation;
		}
		//Returns the int location of the player
		return findPlayer(colour).location();
	}

	@Override
	public int getPlayerTickets(Colour colour, Ticket ticket){
		//Returns the number of tickets of the correct type held by the player
		return findPlayer(colour).tickets().get(ticket);
	}

	//A private helper method to identify a player from playerList based on their colour
	private ScotlandYardPlayer findPlayer(Colour colour){
		//Iterates through all players to find the player with the argument colour
		ScotlandYardPlayer selected = null;
		for (ScotlandYardPlayer player : playerList){
			if(player.colour() == colour){
				selected = player;
			}
		}
		//Returns the selected player
		// requireNonNull() used because selected is initialised to null
		return requireNonNull(selected);
	}

	@Override
	public boolean isGameOver(){
		//Game over if rounds have maxed out
		if(endOfGame()){return true;}
		//Game over if Mr X is stuck and it's the enf of the round
		if(mrXStuck() && endOfRound()){return true;}
		//Game over if Mr X is captured
		if(mrXCaptured()){return true;}
		//Game over if all detectives are stuck
		if(detectivesStuck()){return true;}
		//Game over if Mr X is cornered
		if(mrXCornered()){return true;}
		return false;
	}

	@Override
	public Set<Colour> getWinningPlayers(){
		HashSet<Colour> set = new HashSet<Colour>();
		//Mr X wins if rounds have maxed out
		if(endOfGame()){set.add(mrX().colour());}
		//Detectives win if Mr X is stuck and it's the end of the round
		else if(mrXStuck() && endOfRound()){set = addDetectives(set);}
		//Detectives win if Mr X is captured
		else if(mrXCaptured()){set = addDetectives(set);}
		//Mr X wins if all detectives are stuck
		else if(detectivesStuck()){set.add(mrX().colour());}
		//Detectives win if Mr X is cornered
		else if(mrXCornered()){set = addDetectives(set);}
		//Return the set
		return Collections.unmodifiableSet(set);
	}

	//Adds the detectives to the argument set and returns it
	private HashSet<Colour> addDetectives(HashSet<Colour> set){
		for (ScotlandYardPlayer detective : detectives()){
			set.add(detective.colour());
		}
		return set;
	}

	//Returns whether it is the end of the game
	private boolean endOfGame(){
		if(currentRound == rounds.size() && currentPlayer == playerList.size()-1){
			return true;
		}
		return false;
	}

  //A helper method to return whether it is the end of the round
	private boolean endOfRound(){
		if(currentPlayer == playerList.size() - 1){
			return true;
		}
		return false;
	}

  //A helper method to return a boolean of whether MrX is stuck
	private boolean mrXStuck(){
		//Game over if MrX is stuck
		if(validMoves(mrX()).isEmpty()){
			return true;
		}
		return false;
	}

	//A helper method to return a boolean of whether MrX has been captured
	private boolean mrXCaptured(){
		//Game over if Mr X is captured
		for (ScotlandYardPlayer detective : detectives()){
			if(detective.location() == mrX().location()){
				return true;
			}
		}
		return false;
	}

	//A helper method to return a boolean of whether the detectives are stuck
	private boolean detectivesStuck(){
		//Game over if all detectives are stuck
		for (ScotlandYardPlayer detective : detectives()){
			Set<Move> passSet = new HashSet<>();
			passSet.add(new PassMove(detective.colour()));
			if(!validMoves(detective).equals(passSet)){
				return false;
			}
		}
		return true;
	}

	//A helper method to return a boolean of whether Mr X is cornered
	private boolean mrXCornered(){
		//Gets a list of the occupied locations
		ArrayList<Integer> occupiedLocations = occupiedLocations();
		//Gets a list of the edges from the current node
		List<Edge<Integer, Transport>> edgesFrom = new ArrayList<Edge<Integer, Transport>>(graph.getEdgesFrom(graph.getNode(mrX().location())));
		for (Edge<Integer, Transport> edge : edgesFrom){
			//If the destination of the edge is not occupied, return false
			if(!occupiedLocations.contains(edge.destination().value())){
				return false;
			}
		}
		return true;
	}

  //A private function to return the current ScotlandYardPlayer, not their colour
	private ScotlandYardPlayer getCurrentScotlandYardPlayer(){
		//Returns the colour of the current player
		return playerList.get(currentPlayer);
	}

	@Override
	public Colour getCurrentPlayer(){
		//Returns the colour of the current player
		return playerList.get(currentPlayer).colour();
	}

	@Override
	public int getCurrentRound(){
		//Returns the current round
		return currentRound;
	}

	@Override
	public boolean isRevealRound(){
		//Returns the boolean of the current round as this corresponds to reveal rounds
		return rounds.get(currentRound);
	}

	@Override
	public List<Boolean> getRounds(){
		//Returns an unmodifiable copy of the list of rounds using the Collections.unmodifiableList() method
		return Collections.unmodifiableList(rounds);
	}

	@Override
	public Graph<Integer, Transport> getGraph(){
		//Returns an immutable copy of the graph using the ImmutableGraph() method
		return new ImmutableGraph<Integer, Transport>(graph);
	}

}
