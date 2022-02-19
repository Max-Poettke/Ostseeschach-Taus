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
	
	public boolean isGameOver() {
		if(gameState.getPointsForTeam(gameState.getCurrentTeam()) == 2 || gameState.getRound() >= 30) {
			return true;
		}
		return false;
	}

	public int bewertung(GameState gameState) {
		//Variablen
		int bewertungspunkte = 0;
		
		//Bewertung gewinnen oder verlieren
		/*if(gameState.isOver()) {
		
			if(gameState.getPointsForTeam(gameState.getCurrentTeam())==2 || (gameState.getTurn() == 60 && spielerVordersteLeichtfigur)) {
				bewertungspunkte = Integer.MAX_VALUE;
			}else {
				bewertungspunkte = -Integer.MAX_VALUE;
			}
		}*/
		
		//Bewertung Bernsteine
		bewertungspunkte += gameState.getPointsForTeam(gameState.getCurrentTeam()) * 200;
		bewertungspunkte -= gameState.getPointsForTeam(gameState.getOtherTeam()) * 300;
		
		//Bewertung eigene Figurenanzahl: für jede existierende Figur 10 Punkte + Modifikator Figurenart (1-4)
		int anzahlEigeneFiguren = gameState.getCurrentPieces().size();
		int anzahlGegnerischerFiguren = 0;
		int figurenModifikator = 0;
		
		//log.info("last move {}",gameState.getLastMove().component2());
		
		PieceType figurenart;
		for(int x = 0; x < 8; x++) {
			for(int y = 0; y < 8; y++) {
				Piece piece = gameState.getBoard().get(x, y);
				if(piece != null) {
					if(piece.getTeam().equals(gameState.getOtherTeam())){
						anzahlGegnerischerFiguren++;
						/*
						figurenart = piece.getType();
					    switch (figurenart) {
					        case Herzmuschel:
					            figurenModifikator += HERZMUSCHEL;
					            break;
					        case Moewe:
					        	figurenModifikator += MOEWE; 
					            break;
					        case Robbe:
					        	figurenModifikator += ROBBE;
					            break;
					        case Seestern:
					        	figurenModifikator += SEESTERN;
					        	break;
					        default:
					            System.out.println("Fehler bei Figurenbewertung");
					            break;
					    }
					    */
					}
				}
			}
		}
		log.info("test: {}", gameState.getBoard().get(gameState.getLastMove().component2()).getType());
		PieceType type = gameState.getBoard().get(gameState.getLastMove().component2()).getType();
		switch (type) {
			case Seestern:
				bewertungspunkte ++;
				break;
			default:
				bewertungspunkte --;
		}
		if(gameState.getCurrentTeam() == gameState.getStartTeam()) {
			bewertungspunkte += anzahlEigeneFiguren - anzahlGegnerischerFiguren;
			//log.info("Punkte für uns: {}, {}", bewertungspunkte, gameState.getCurrentTeam());
		} else {
			bewertungspunkte -= anzahlEigeneFiguren - anzahlGegnerischerFiguren;
			//log.info("Punkte für gegner: {}, {}", bewertungspunkte, gameState.getCurrentTeam());
		}
		
		log.info("Punkte: {}", bewertungspunkte);
		return bewertungspunkte;
	}

	@Override
	public Move calculateMove() {
		maxPoints = 0;
		long startTime = System.currentTimeMillis();
		log.info("Es wurde ein Zug von {} angefordert.", gameState.getCurrentTeam());
		Move move = null;
		
		List<Move> possibleMoves = gameState.getPossibleMoves();
		
		for (Move nextMove : possibleMoves) {
			GameState clone = gameState.clone();
			clone.performMove(nextMove);
			
			double points = alphaBetaPruning(clone, 2, -10000, 10000, true);
			log.info("Punkte danach: {}", points);
			if(points > maxPoints) {
				maxPoints = points;
				move = nextMove;
				log.info("GameState: {}", clone.getBoard());
			}
		}
		
		/*for (Move nextMove : possibleMoves) {
			
		}*/
/*
		anzahlGegnerischeFiguren = 0;
		for(int x = 0; x < 8; x++) {
			for(int y = 0; y < 8; y++) {
				Piece piece = gameState.getBoard().get(x, y);
				if(piece != null && piece.getTeam().equals(gameState.getOtherTeam())) {
					anzahlGegnerischeFiguren++;
				}
			}
		}


		int maxPoints = gameState.getPointsForTeam(gameState.getCurrentTeam());

		for(Move nextMove : possibleMoves) {
			GameState clone = gameState.clone();
			clone.performMove(nextMove);
			int points = getPoints(clone);
			if(points > maxPoints) {
				maxPoints = points;
				move = nextMove;
			}
		}
		
	
	
//		for(int i = 0; i < possibleMoves.size(); i++) {
//			Move newMove = possibleMoves.get(i);
//		}
*/
		log.info("Sende {} nach {}ms.", move, System.currentTimeMillis() - startTime);
		return move;
	}
	
	public double alphaBetaPruning(GameState gameState, int depth, double bestOverall, double worstOverall, boolean isMaximizing) {
		
		if (depth == 0 || gameState.isOver()) {
			return bewertung(gameState);
		}
		
		List<Move> possibleMoves = gameState.getPossibleMoves();
		
		if(isMaximizing) {
			maxEval = -1e9;
			for (Move nextMove : possibleMoves) {
				GameState clone = gameState.clone();
				clone.performMove(nextMove);
				evaluation = alphaBetaPruning(clone , depth - 1, bestOverall, worstOverall, false);
				if(evaluation > maxEval) maxEval = evaluation;
				if(evaluation > bestOverall) bestOverall = evaluation;
				if(worstOverall <= bestOverall) {
					log.info("broke out of loop");
					break;
				}
			}
			//log.info("maxEval: {}", maxEval);
			return maxEval;
		} else {
			minEval = 1e10;
			for (Move nextMove : possibleMoves) {
				GameState clone = gameState.clone();
				clone.performMove(nextMove);
				evaluation = alphaBetaPruning(clone, depth - 1, bestOverall, worstOverall, true);
				if(evaluation < minEval) minEval = evaluation;
				if(evaluation < worstOverall) worstOverall= evaluation;
				if(worstOverall <= bestOverall) {
					log.info("broke out of loop");
					break;
				}
			}
			//log.info("minEval: {}", minEval);
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