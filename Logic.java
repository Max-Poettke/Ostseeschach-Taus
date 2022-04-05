package sc.player2022.logic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.api.plugins.IGameState;
import sc.api.plugins.ITeam;
import sc.api.plugins.Team;
import sc.player.IGameHandler;
import sc.plugin2022.Board;
import sc.plugin2022.Coordinates;
import sc.plugin2022.GameState;
import sc.plugin2022.Move;
import sc.plugin2022.Piece;
import sc.plugin2022.PieceType;
import sc.plugin2022.Vector;
import sc.plugin2022.util.Constants;
import sc.shared.GameResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 * Das Herz des Clients: Eine sehr simple Logik, die ihre Zuege zufaellig
 * waehlt, aber gueltige Zuege macht.
 * Ausserdem werden zum Spielverlauf Konsolenausgaben gemacht.
 */

public class Logic implements IGameHandler, ITeam{
	private static final Logger log = LoggerFactory.getLogger(Logic.class);

	/** Aktueller Spielstatus. */
	private GameState gameState;
	
	//Werte der einzelnen Spielfiguren (je höher, desto besser)
	final static public int HERZMUSCHEL = 1;
	final static public int MOEWE = 2;
	final static public int ROBBE = 3;
	final static public int SEESTERN = 4;
	
	//sonstige Variablen (muss noch überarbeitet werden, für aktuelle Logik)
	int anzahlGegnerischeFiguren;
	int anzahlEigeneFiguren;
	int simulationTurn;
	int spielerVordersteLeichtfigur;
	double maxEval;
	double minEval;
	double evaluation;
	double maxPoints = -1;
	int searchDepth = 2;
	int count = 0;
	
	//Hilfsfunktionen Bewertung
	//Hilfsfunktion: gibt 1 zurück, wenn das CurrentTeam die Leichtfiguren weiter vorne hat
	public int gewinnerBestimmenZugweite(GameState gameState){ 
		int gewinner = 0;
		Board board = gameState.getBoard();
		int gegner=0;
		int selber=0;
		log.info("Am Zug: {}", gameState.getCurrentTeam());
		for(int x=1; x<8; x++){
			for(int y=0; y<8; y++){
				Piece ownPiece = board.get(7-x, y);
				Piece otherPiece = board.get(x, y);
				if(ownPiece != null && ownPiece.getType().isLight() && ownPiece.getTeam() == gameState.getCurrentTeam()) {
					selber++;
				}
				if(otherPiece != null && otherPiece.getType().isLight() && otherPiece.getTeam() == gameState.getOtherTeam()) {
					gegner++;
				}
			}
			if(gegner!=selber) {
				break;
			}
		}
		if(gegner<selber){
			log.info("CurrentTeam hat gewonnenTeam gegner: {} Team selber:{}", gegner, selber);
			gewinner = 1;
		}else if(gegner>selber){
			log.info("OtherTeam hat gewonnen. Team gegner: {} Team selber:{}", gegner, selber);
		}
		
		return gewinner;
	}
	
	//Hilfsfunktion: Zugweite der Figuren
	//Werte: figureninformation[0] = abstandTeam1 | figureninformation[1] = abstandTeam2 | 
	//Bewertung: 1 Punkt pro Feld, Seesterne doppelt
	//Beispielwerte points: 5, -6, 4, 10, -2
	public int figureninformation(GameState gameState){
		int points = 0;
		int[] figureninformation = new int[2];
		Board board = gameState.getBoard();
		for(int y=0; y<8; y++){
			for(int x=1; x<7; x++){
				Piece aktuelleFigur = board.get(x,y);
				if(aktuelleFigur != null && aktuelleFigur.getType().isLight()){
					if(aktuelleFigur.getTeam().toString() == "ONE") {
						figureninformation[0] += x;
						figureninformation[0] += figurenInteraktionen(aktuelleFigur, x, y, gameState);
						
						//Falls eine Figur ein Turm ist soll sie Priorität beim Ziehen bekommen
						if(aktuelleFigur.getCount() > 1) {
							figureninformation[0] += 15;
						}
						
						if(aktuelleFigur.getType() == PieceType.Seestern) {
							figureninformation[0]+=x;
						}
						
						
					}else if(aktuelleFigur.getTeam().toString() == "TWO") {
						figureninformation[1] += 7-x;
						figureninformation[1] += figurenInteraktionen(aktuelleFigur, x, y, gameState);
						
						//Falls eine Figur ein Turm ist soll sie Priorität beim Ziehen bekommen
						if(aktuelleFigur.getCount() > 1) {
							figureninformation[1] += 15;
						}
							
						if(aktuelleFigur.getType() == PieceType.Seestern) {
							figureninformation[1]+= 7-x;
						}
					}
				}
			}
		}
		points = figureninformation[0] - figureninformation[1];
		//log.info("Bewertung roter Abstands: {} , Bewertung blauer Abstand: {} Gesamtwertung: {}", figureninformation[0], figureninformation[1], points);	
		return points;
	}
	
	//todo: Weg finden gegnerische Figurenkoordinaten zu finden und pro schlagbarer Figur points hochzählen
	public int figurenInteraktionen(Piece piece, int pieceX, int pieceY, GameState gameState) {
		int points = 0;
		int attackedPieces = 0;
		int protectedPieces = 0;
		Board board = gameState.getBoard();
		List<Vector> futureMoves = piece.getPossibleMoves();
		log.info("currentTurn: {}", gameState.getTurn());
		if(gameState.getTurn() == 0) return 0;
		Map<Coordinates, Piece> allyPiecesMap = new GameState(board, gameState.getTurn()).getCurrentPieces();
		List<Coordinates> allyPieces = new ArrayList<Coordinates>();
		
		Map<Coordinates, Piece> enemyPiecesMap = new GameState(board, gameState.getTurn() - 1).getCurrentPieces();
		List<Coordinates> enemyPieces = new ArrayList<Coordinates>();
		
		//add only the coordinates of the pieces to a seperate List for later use
		for(Map.Entry<Coordinates, Piece> pair : allyPiecesMap.entrySet()) {
			allyPieces.add(pair.getKey());
		}
		
		for(Map.Entry<Coordinates, Piece> pair : enemyPiecesMap.entrySet()){
			enemyPieces.add(pair.getKey());
		}
		
		for(Vector futureMove : futureMoves) {
			pieceX += futureMove.component1();
			pieceY += futureMove.component2();
			
			//Check how many Pieces are being attacked by the moving piece
			for(Coordinates enemyPiece : enemyPieces) {
				if(pieceX == enemyPiece.getX() && pieceY == enemyPiece.getY()) {
					attackedPieces ++;
				}
			}
			
			//Check how many Pieces are being Protected by the moving piece
			for(Coordinates allyPiece : allyPieces) {
				if(pieceX == allyPiece.getX() && pieceY == allyPiece.getY()) {
					protectedPieces ++;
				}
			}
		}
		
		points = attackedPieces + protectedPieces;
		return points;
	}
	
	//Hilfsfunktion: Figurenanzahl, zählt die Figuren (inkl. Figurenmodifikation)
	//Werte: figurenzahl[0] = Anzahl rote Figuren | figurenanzahl[1] = Anzahl blaue Figuren
	//Bewertung: 50 pro Figur + Figurenmodifikator
	//Beispielwerte points: 1, -158, 1, 48, -2
	public int figurenanzahl(GameState gameState) {
		int points = 0;
		int[] punkte = new int[2];
		Board board = gameState.getBoard();
		for(int y=0; y<8; y++){
			for(int x=0; x<8; x++){
				Piece aktuelleFigur = board.get(x,y);
				if(aktuelleFigur != null) {
					PieceType figurenart = aktuelleFigur.getType();
					int figurenpunkte = 0;
				    switch (figurenart) {
				        case Herzmuschel:
				            figurenpunkte += HERZMUSCHEL;
				            break;
				        case Moewe:
				        	figurenpunkte += MOEWE; 
				            break;
				        case Robbe:
				        	figurenpunkte += ROBBE;
				            break;
				        case Seestern:
				        	figurenpunkte += SEESTERN;
				        	break;
				        default:
				            System.out.println("Fehler bei Figurenbewertung");
				            break;
				    }
					if(aktuelleFigur.getTeam().toString() == "ONE") {
						punkte[0]+= (figurenpunkte +50);
					}else{
						punkte[1]+= (figurenpunkte +50);
					}
				}
			}
		}
		points = punkte[0]-punkte[1];
		//log.info("Figurenanzahl rot: {} , Figurenanzahl blau: {}, Punkte: {}", punkte[0], punkte[1], points);
		return points;
	}
	
	//Hilfsfunktion: Bernsteine
	//Werte: bernsteine[0] = bernsteinpunkte Team1 | bernsteine[1] = bernsteinpunkte Team2
	//Bewertung:200 pro Bernstein
	//Beispielwertung: -400, 0, 400, 400, 400
	public int bernsteine(GameState gameState) {
		int points = 0;
		int[] bernsteine = new int[2];
		 bernsteine[0]+= gameState.getPointsForTeam(Team.ONE) * 200;
		 bernsteine[1]+= gameState.getPointsForTeam(Team.TWO) * 200;
		 points = bernsteine[0]-bernsteine[1];
		 //log.info("Bernsteine Team1: {} , Bernsteine Team2: {} , Punkte: {}", bernsteine[0], bernsteine[1], points);
		return points;
	}
	
	//Hilfsfunktion: Türme
		//Werte: turm[0] = Anzahl Türme Team1 | turm[1] = Anzahl Turm Team2
		//Bewertung: 20 pro Turm
		//Beispielwertung: 0, 0, 20, 0, 20
	public int turm(GameState gameState) {
		int points = 0;
		int[] turm = new int[2];
		Board board = gameState.getBoard();
		for(int y=0; y<8; y++){
			for(int x=0; x<8; x++){
				Piece piece = board.get(x,y);
				if(piece != null && piece.getCount() >1) {
					if(piece.getTeam() == Team.ONE) {
						turm[0]+= 20;
					}else {
						turm[1]+= 20;
					}
				}
			}
		}
		points = turm[0]-turm[1];
		 //log.info("Anzahl Turm Team1: {} , Anzahl Turm Team2: {} , Punkte: {}", turm[0], turm[1], points);
		return points;
	}	
	
	public int freieZüge(GameState gameState) {
		int points = 0;
		Board board = gameState.getBoard();
		Piece piece = board.get(gameState.getLastMove().component2());
		
		if(piece == null) {
			return 0;
		}
		
		List<Vector> futureMoves = piece.getPossibleMoves();
		int freeMoveCount = futureMoves.size();
		
		List<Move> opponentMoves = new GameState(gameState.getBoard(), gameState.getTurn() - 1).getPossibleMoves();
		for(Vector futureMove : futureMoves) {
			for (Move possibleEnemyMove : opponentMoves) {
				if (possibleEnemyMove.component2().getX() == gameState.getLastMove().component2().getX() + futureMove.getDx()) {
					if(possibleEnemyMove.component2().getY() == gameState.getLastMove().component2().getY() + futureMove.getDy()) {
						freeMoveCount --;
						break;
					}
					break;
				}
			}
		}
		
		points += freeMoveCount;
		return points;
	}
	
	//Bewertungsfunktion
	// Beispielwerte bewertungspunkte: x, -164, 405, 458, 396
	public int bewertung(GameState gameState) {
		int bewertungspunkte = 0;
		//Bewertung gewinnen oder verlieren
		if(gameState.isOver()) {
			if(gameState.getPointsForTeam(gameState.getCurrentTeam())>=2
					|| (gameState.getTurn() == 60 && gameState.getPointsForTeam(gameState.getCurrentTeam()) > gameState.getPointsForTeam(gameState.getOtherTeam()))
					|| ((gameState.getTurn() == 60 && gameState.getPointsForTeam(gameState.getCurrentTeam()) == gameState.getPointsForTeam(gameState.getOtherTeam()) && gewinnerBestimmenZugweite(gameState) == 1))
			) {
				bewertungspunkte = Integer.MAX_VALUE - 1;
				log.info("Gewonnen");
				return bewertungspunkte;
			}else {
				bewertungspunkte = -Integer.MAX_VALUE + 1;
				log.info("Verloren");
				return bewertungspunkte;
			}
		}
		bewertungspunkte += figureninformation(gameState);
		bewertungspunkte += figurenanzahl(gameState) ;
		bewertungspunkte += bernsteine(gameState);
		bewertungspunkte += turm(gameState);
		bewertungspunkte += freieZüge(gameState);
		log.info("Zug: {}", gameState.getLastMove());
		log.info("Punkte Bewertungsfunktion: {}", bewertungspunkte);
		return bewertungspunkte;
	}
	
	@Override
	public Move calculateMove() {
		long startTime = System.currentTimeMillis();
		log.info("Es wurde ein Zug von {} angefordert.", gameState.getCurrentTeam());
		
		Move move = null;
		List<Move> possibleMoves = gameState.getPossibleMoves();
		if(gameState.getCurrentTeam() == Team.ONE){
			maxPoints = -Integer.MAX_VALUE;
		}else {
			maxPoints = Integer.MAX_VALUE;
		}
		count = 0;
		for (Move nextMove : possibleMoves) {
			GameState clone = gameState.clone();
			clone.performMove(nextMove);
			
			double points = 0;
			if(clone.getCurrentTeam() == Team.TWO){
				log.info("Meine Lieblingsfarbe ist rot");
				points += alphaBetaPruning(clone, searchDepth, -1000, 1000, false);
				log.info("Punkte calculateMove: {}", points);
				if(points > maxPoints) {
					maxPoints = points;
					move = nextMove;
				}
			}else {
				log.info("Meine Lieblingsfarbe ist blau");
				points += alphaBetaPruning(clone, searchDepth, -1000, 1000, true);
				log.info("Punkte calculateMove: {}", points);
				if(points < maxPoints) {
					maxPoints = points;
					move = nextMove;
				}
			}
		}
		log.info("Sende {} nach {}ms., points: {}", move, System.currentTimeMillis() - startTime, maxPoints);
		log.info("Count: {}", count);
		return move;
	}
	
	
	// really brainbreaking rekursive function that may or may not work
	public double alphaBetaPruning(GameState currGameState, int depth, double alpha, double beta, boolean isMaximising) {
		if(depth == 0 || currGameState.isOver()) {
			int punkte = bewertung(currGameState);
			log.info("Punkte Pruning: {}", punkte); 
			return punkte;
		}
		count++;
		List<Move> possibleMoves = currGameState.getPossibleMoves();
		log.info("depth {}", depth);
		
		if(isMaximising) {
			log.info("Überlege für Spieler1");
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
			log.info("Überlege für Spieler2");
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


	public void onGameOver(GameResult data) {
		log.info("Das Spiel ist beendet, Ergebnis: {}", data);
		bewertung(gameState);
	}

	@Override
	public int getIndex() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ITeam opponent() {
		// TODO Auto-generated method stub
		return null;
	}

	
}