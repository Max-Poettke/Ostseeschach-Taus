package sc.player2022.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.api.plugins.IGameState;
import sc.api.plugins.IMove;
import sc.api.plugins.ITeam;
import sc.player.IGameHandler;
import sc.plugin2022.Coordinates;
import sc.plugin2022.GameState;
import sc.plugin2022.Move;
import sc.plugin2022.Piece;
import sc.plugin2022.PieceType;
import sc.protocol.room.RoomMessage;
import sc.shared.GameResult;
import sc.shared.IMoveMistake;
import sc.shared.InvalidMoveException;
import sc.util.GameResultConverter;

import java.util.List;

/**
 * Das Herz des Clients: Eine sehr simple Logik, die ihre Zuege zufaellig
 * waehlt, aber gueltige Zuege macht.
 * <p>
 * Ausserdem werden zum Spielverlauf Konsolenausgaben gemacht.
 */
public class Logic implements IGameHandler{
	private static final Logger log = LoggerFactory.getLogger(Logic.class);

	/** Aktueller Spielstatus. */
	private GameState gameState;
	
	public void onGameOver(GameResult data) {
		log.info("Das Spiel ist beendet, Ergebnis: {}", data);
	}

	int anzahlGegnerischeFiguren;
	double maxEval;
	double minEval;
	double evaluation;
	double maxPoints = -1;
	
	//Werte der einzelnen Spielfiguren (je höher, desto besser)
	final static public int HERZMUSCHEL = 1;
	final static public int MOEWE = 2;
	final static public int ROBBE = 3;
	final static public int SEESTERN = 4;
	
	//sonstige Variablen (muss noch überarbeitet werden)
	int anzahlEigeneFiguren;
	int simulationTurn;
	int spielerVordersteLeichtfigur;
	int searchDepth = 4;
	
	public boolean isGameOver() {
		if(gameState.getPointsForTeam(gameState.getCurrentTeam()) == 2 || gameState.getRound() >= 30) {
			return true;
		}
		return false;
	}

	public int bewertung(GameState gameState) {
		//Variablen
		int bewertungspunkte = 0;
		
		//Bewertung eigene Figurenanzahl: für jede existierende Figur 10 Punkte (1-4)
		int anzahlEigeneFiguren = gameState.getCurrentPieces().size();
		int anzahlGegnerischerFiguren = 0;
		
		//PieceType figurenart;
		for(int x = 0; x < 8; x++) {
			for(int y = 0; y < 8; y++) {
				Piece piece = gameState.getBoard().get(x, y);
				if(piece != null) {
					if(piece.getTeam().equals(gameState.getOtherTeam())){
						anzahlGegnerischerFiguren++;
					}
				}
			}
		}
		
		//looks for the pieceType for making a piecemove hierarchy
		bewertungspunkte -= (anzahlEigeneFiguren - anzahlGegnerischerFiguren) * 10;
		
		
		bewertungspunkte -= gameState.getPointsForTeam(gameState.getCurrentTeam()) * 200;
		bewertungspunkte += gameState.getPointsForTeam(gameState.getOtherTeam()) * 300;
		
		log.info("Punkte: {}, move: {}", bewertungspunkte, gameState.getLastMove());
		return bewertungspunkte;
	}

	@Override
	public Move calculateMove() {
		long startTime = System.currentTimeMillis();
		log.info("Es wurde ein Zug von {} angefordert.", gameState.getCurrentTeam());
		
		Move move = null;
		List<Move> possibleMoves = gameState.getPossibleMoves();
		maxPoints = -500;
		
		for (Move nextMove : possibleMoves) {
			GameState clone = gameState.clone();
			
			if(clone.getBoard().get(nextMove.getFrom()).getType() == PieceType.Seestern) {
				clone.performMove(nextMove);
				
				double points = 0;
				
				if(clone.getBoard().get(clone.getLastMove().component2()) != null) {
					PieceType type = clone.getBoard().get(clone.getLastMove().component2()).getType();
					switch (type) {
						case Seestern:
							points ++;
							if(clone.getLastMove().getTo().getX() > clone.getLastMove().getFrom().getX()) {
								points = points + 5;
							}
							break;
						default:
							points --;
					}
				}
				
				points += alphaBetaPruning(clone, searchDepth, -1000, 1000, true);
				
				if(points > maxPoints) {
					maxPoints = points;
					move = nextMove;
				}
			}
		}

		log.info("Sende {} nach {}ms., points: {}", move, System.currentTimeMillis() - startTime, maxPoints);
		return move;
	}
	
	
	// really brainbreaking rekursive function that may or may not work
	public double alphaBetaPruning(GameState currGameState, int depth, double alpha, double beta, boolean isMaximising) {
		if(depth == 0 || currGameState.isOver()) {
			return bewertung(currGameState);
		}
		
		List<Move> possibleMoves = currGameState.getPossibleMoves();
		log.info("depth {}", depth);
		
		if(isMaximising) {
			maxEval = -1e9;
			for(Move nextMove : possibleMoves) {
				GameState clone = currGameState.clone();
				clone.performMove(nextMove);
				evaluation = alphaBetaPruning(clone, depth - 1, alpha, beta, false);
				if (evaluation > maxEval) maxEval = evaluation;
				if (evaluation > alpha) alpha = evaluation;
				if (beta <= alpha) break;
			}
			return maxEval;
		} else {
			minEval = 1e9;
			for(Move nextMove : possibleMoves) {
				GameState clone = currGameState.clone();
				clone.performMove(nextMove);
				evaluation = alphaBetaPruning(clone, depth - 1, alpha, beta, true);
				if (evaluation < minEval) minEval = evaluation;
				if (evaluation < beta) beta = evaluation;
				if (beta <= alpha) break;
			}
			return minEval;
		}
	}

	@Override
	public void onUpdate(IGameState gameState) {
		this.gameState = (GameState) gameState;
		log.info("Zug: {} Dran: {}", gameState.getTurn(), gameState.getCurrentTeam());
	}

	@Override
	public void onError(String error) {
		log.warn("Fehler: {}", error);
	}
}

class FigurenDaten{
	public Coordinates[] Robbe = new Coordinates[2];
	public Coordinates[] Moewe = new Coordinates[2];
	public Coordinates[] Seestern = new Coordinates[2];
	public Coordinates[] Herzmuschel = new Coordinates[2];
	
	public FigurenDaten(PieceType[] figurenTypen, Coordinates[] koordinaten){
		int[] positionen = new int[4];
		for(int i=0; i< figurenTypen.length && figurenTypen[i]!=null; i++) {
		    switch (figurenTypen[i]) {
		        case Herzmuschel:
		        	Herzmuschel[positionen[0]++] = koordinaten[i];
		            break;
		        case Moewe:
		        	Moewe[positionen[1]++] = koordinaten[i];
		            break;
		        case Robbe:
		        	Robbe[positionen[2]++] = koordinaten[i];
		            break;
		        case Seestern:
		        	Seestern[positionen[3]++] = koordinaten[i];
		        	break;
		    }
			
		}
	}
}