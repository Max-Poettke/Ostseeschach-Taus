package sc.player2022.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.api.plugins.IGameState;
import sc.api.plugins.IMove;
import sc.player.IGameHandler;
import sc.plugin2022.GameState;
import sc.plugin2022.Move;
import sc.plugin2022.Piece;
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
	int maxEval;
	int minEval;
	int evaluation;
	int maxPoints = -1;
	
	public boolean isGameOver() {
		if(gameState.getPointsForTeam(gameState.getCurrentTeam()) == 2 || gameState.getRound() >= 30) {
			return true;
		}
		return false;
	}
	
	public int bewertung(GameState clone) {
		
		anzahlGegnerischeFiguren = 0;
		for(int x = 0; x < 8; x++) {
			for(int y = 0; y < 8; y++) {
				Piece piece = clone.getBoard().get(x, y);
				if(piece != null && piece.getTeam().equals(clone.getOtherTeam())) {
					anzahlGegnerischeFiguren++;
				}
			}
		}
		
		int points = 0;
		
		points += clone.getPointsForTeam(clone.getCurrentTeam());
		points += clone.getCurrentPieces().size() - anzahlGegnerischeFiguren;
		//log.info("Gegnerische Figuren {}", points);
		return points;
	}

	@Override
	public Move calculateMove() {
		maxPoints = -1000;
		System.out.println("Let's go");
		long startTime = System.currentTimeMillis();
		log.info("Es wurde ein Zug von {} angefordert.", gameState.getCurrentTeam());
		
		Move move = null;
		
		List<Move> possibleMoves = gameState.getPossibleMoves();
		for (Move nextMove : possibleMoves) {
			GameState clone = gameState.clone();
			clone.performMove(nextMove);
			int points = alphaBetaPruning(clone, 5, -10000, 10000, true);
			log.info("Punkte des Moves: {}",points);
			if(points >= maxPoints) {
				maxPoints = points;
				move = nextMove;
				log.info("Der move war {}", move);
			}
		}
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
	
	public int alphaBetaPruning(GameState gameState, int depth, int alpha, int beta, boolean isMaximizing) {
		if (depth == 0 || gameState.isOver()) {
			//log.info("Gamestate is {}", gameState.getBoard());
			return bewertung(gameState);
		}
		
		
		
		List<Move> possibleMoves = gameState.getPossibleMoves();
		//log.info("Possible moves are: {}", possibleMoves);
		log.info("depth is {}", depth);
		//log.info("Maximising is {}", isMaximizing);
		if(isMaximizing) {
			maxEval = -10^10;
			
			for (Move nextMove : possibleMoves) {
				
				GameState clone = gameState.clone();
				clone.performMove(nextMove);
				evaluation = alphaBetaPruning(clone , depth - 1, alpha, beta, false);
				if (evaluation > 0) log.info("move is {}", nextMove);
				if(evaluation > maxEval) maxEval = evaluation;
				if(evaluation > alpha) alpha = evaluation;
				//log.info("this is {} alpha, {} beta", alpha,beta);
				//if(beta >= alpha) break;
				//log.info("this should have happened a");
				return maxEval;
			}
		} else if (isMaximizing != true){
			minEval = 10^10;
			for (Move nextMove : possibleMoves) {
				GameState clone = gameState.clone();
				clone.performMove(nextMove);
				
				evaluation = alphaBetaPruning(clone, depth - 1, alpha, beta, true);
				if(evaluation > 0) log.info("move is {}", nextMove);
				if(evaluation < minEval) minEval = evaluation;
				if(evaluation < beta) beta = evaluation;
				//if(beta <= alpha) break;
				//log.info("this should have happened b");
				return minEval;
			}
		}
		
		log.info("this shouldnt have happened");
		return bewertung(gameState); // this is just because im lazy and the compiler wants it. It will never be called
	}

	public int getPoints(GameState clone) {
		int points = 0;
		
		points += clone.getPointsForTeam(gameState.getCurrentTeam());
		points += anzahlGegnerischeFiguren - clone.getCurrentPieces().size();
		return points;
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
	
	//This is a test
}
