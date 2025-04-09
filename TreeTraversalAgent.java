package src.pas.pokemon.agents.pokemon.agents;



// SYSTEM IMPORTS....feel free to add your own imports here! You may need/want to import more from the .jar!
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Team;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.SwitchMove;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.utils.Pair;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;

//import src.pas.pokemon.agents.Node;
//import src.pas.pokemon.agents.heuristics;



import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


// JAVA PROJECT IMPORTS


public class TreeTraversalAgent
    extends Agent
{

	private class StochasticTreeSearcher
        extends Object
        implements Callable<Pair<MoveView, Long> >  // so this object can be run in a background thread
	{

        // TODO: feel free to add any fields here! If you do, you should probably modify the constructor
        // of this class and add some getters for them. If the fields you add aren't final you should add setters too!
		private final BattleView rootView;
        private final int maxDepth;
        private final int myTeamIdx;

        // If you change the parameters of the constructor, you will also have to change
        // the getMove(...) method of TreeTraversalAgent!
		public StochasticTreeSearcher(BattleView rootView, int maxDepth, int myTeamIdx)
        {
            this.rootView = rootView;
            this.maxDepth = maxDepth;
            this.myTeamIdx = myTeamIdx;
        }

        // Getter methods. Since the default fields are declared final, we don't need setters
        // but if you make any fields that aren't final you should give them setters!
		public BattleView getRootView() { return this.rootView; }
        public int getMaxDepth() { return this.maxDepth; }
        public int getMyTeamIdx() { return this.myTeamIdx; }

		/**
		 * TODO: implement me!
		 * This method should perform your tree-search from the root of the entire tree.
         * You are welcome to add any extra parameters that you want! If you do, you will also have to change
         * The call method in this class!
		 * @param node the node to perform the search on (i.e. the root of the entire tree)
		 * @return The MoveView that your agent should execute
		 */
        public MoveView stochasticTreeSearch(BattleView rootView) //, int depth)
        {
            if (rootView == null) {
                throw new IllegalArgumentException();
            }
            Node root = new Node(Node.MAX_NODE, new ArrayList<>(), 1.0, rootView, 0, myTeamIdx, myTeamIdx); 
            MoveView bestMove = null;
            double bestUtilityValue = Double.NEGATIVE_INFINITY;

            List<Node> children = root.makeChildren(null, myTeamIdx, root.getMyOpponentTeamIdx(), maxDepth);

            for(Node child: children){
                double utilityValue = stochasticHelper(child,Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
                //System.out.println("Child utility: " + utilityValue);
                MoveView candidateMove = child.getLastMoveView();
                if (candidateMove == null) {
                    continue; // skip this child if no valid move
                }
                //System.out.println("Considered move: " + child.getLastMoveView().getName() + " => utility: " + utilityValue);
                if(utilityValue>bestUtilityValue){
                    bestUtilityValue = utilityValue;
                    bestMove = candidateMove;
                }
                
            }
            if (bestMove == null && mustSwitch(rootView)) {  
                bestMove = getSwitchMove(rootView);
            }
            if (bestMove == null) {
                throw new RuntimeException("Error: No valid move found at root decision node!");
            }

            return bestMove;

        }

        private boolean mustSwitch(BattleView rootView) {
            return rootView.getTeamView(myTeamIdx).getActivePokemonView().hasFainted();
        }
        private MoveView getSwitchMove(BattleView rootView) {
            TeamView myTeam = rootView.getTeamView(myTeamIdx);
            
            for (int idx = 0; idx < myTeam.size(); idx++) {
                if (!myTeam.getPokemonView(idx).hasFainted()) {
                    return new SwitchMove(idx).getView();
                }
            }
            return null; 
        }

        private double stochasticHelper(Node node, double alpha, double beta) {
            //double utility = 0.0;
            if(node.isTerminal(maxDepth)){
                //System.out.println("leaf node utility = " + node.getHeuristic());
                double h = node.getHeuristic();
                if (h == 0){
                    return 0.0;  // or a default value like Double.MIN_VALUE
                }
                return h;
            }

            List<Node> children = node.makeChildren(node.getLastMoveView(),node.getCurrentPlayerTeamIdx(),node.getOtherPlayerTeamIdx(), maxDepth);
            
            if(node.NodeType == node.MAX_NODE){
                double maxUtility = Double.NEGATIVE_INFINITY;

                for(Node child:children){
                    double utility = stochasticHelper(child, alpha, beta);
                    maxUtility = Math.max(maxUtility, utility);
                    alpha = Math.max(alpha, utility);
                    if (alpha >= beta){
                        break;
                    }
                }
                return maxUtility;

            } else if(node.NodeType== node.MIN_NODE){
                double minUtility = Double.POSITIVE_INFINITY;
                for(Node child:children){
                    double utility = stochasticHelper(child, alpha, beta);
                    minUtility = Math.min(minUtility, utility);
                    beta = Math.min(beta, utility);
                    if (alpha >= beta) {
                        break;
                    }
                }
                return minUtility;
            } else{
                double chanceUtility = 0.0;
                for(Node child:children){
                    double p = child.getProbability();
                    double cu = stochasticHelper(child, alpha, beta);
                    chanceUtility += p * cu;
                }
                return chanceUtility;
            }
        }

        @Override
        public Pair<MoveView, Long> call() throws Exception
        {
            double startTime = System.nanoTime();

            MoveView move = this.stochasticTreeSearch(this.getRootView());
            double endTime = System.nanoTime();

            return new Pair<MoveView, Long>(move, (long)((endTime-startTime)/1000000));
        }
		
	}

	private final int maxDepth;
    private long maxThinkingTimePerMoveInMS;

	public TreeTraversalAgent()
    {
        super();
        this.maxThinkingTimePerMoveInMS = 180000 * 2; // 6 min/move
        this.maxDepth = 3; // set this however you want
    }

    /**
     * Some constants
     */
    public int getMaxDepth() { return this.maxDepth; }
    public long getMaxThinkingTimePerMoveInMS() { return this.maxThinkingTimePerMoveInMS; }

    @Override
    public Integer chooseNextPokemon(BattleView view)
    {
        // TODO: replace me! This code calculates the first-available pokemon.
        // It is likely a good idea to expand a bunch of trees with different choices as the active pokemon on your
        // team, and see which pokemon is your best choice by comparing the values of the root nodes.

        int bestIdx=-1;
        double bestUtilityValue = Double.NEGATIVE_INFINITY;

        for(int idx = 0; idx < this.getMyTeamView(view).size(); ++idx)
        {
            if(!this.getMyTeamView(view).getPokemonView(idx).hasFainted())
            {
                SwitchMove switchMove = new SwitchMove(idx);
                Move.MoveView switchView = switchMove.getView();
                Node newRoot = new Node(Node.CHANCE_NODE, new ArrayList<>(), 1.0, view, 0, getMyTeamIdx(), getMyTeamIdx());
                newRoot.setLastMoveView(switchView);
                List<Node> children = newRoot.makeChildren(switchView, newRoot.getCurrentPlayerTeamIdx(), newRoot.getOtherPlayerTeamIdx(), this.getMaxDepth());
                double utility=0.0;
                for(Node child: children){
                    utility += child.getProbability() * new StochasticTreeSearcher(view, maxDepth, getMyTeamIdx()).stochasticHelper(child, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
                }
                if(utility>bestUtilityValue){
                    bestUtilityValue=utility;
                    bestIdx = idx;
                }

            }
        }
        if(bestIdx>=0){
            return bestIdx;
        } else{
            return null;
        }
    }

    /**
     * This method is responsible for getting a move selected via the minimax algorithm.
     * There is some setup for this to work, namely making sure the agent doesn't run out of time.
     * Please do not modify.
     */
    @Override
    public MoveView getMove(BattleView battleView)
    {

        // will run the minimax algorithm in a background thread with a timeout
        ExecutorService backgroundThreadManager = Executors.newSingleThreadExecutor();

        // preallocate so we don't spend precious time doing it when we are recording duration
        MoveView move = null;
        long durationInMs = 0;

        // this obj will run in the background
        StochasticTreeSearcher searcherObject = new StochasticTreeSearcher(
            battleView,
            this.getMaxDepth(),
            this.getMyTeamIdx()
        );

        // submit the job
        Future<Pair<MoveView, Long> > future = backgroundThreadManager.submit(searcherObject);

        try
        {
            // set the timeout
            Pair<MoveView, Long> moveAndDuration = future.get(
                this.getMaxThinkingTimePerMoveInMS(),
                TimeUnit.MILLISECONDS
            );

            // if we get here the move was chosen quick enough! :)
            move = moveAndDuration.getFirst();
            durationInMs = moveAndDuration.getSecond();

            // convert the move into a text form (algebraic notation) and stream it somewhere
            // Streamer.getStreamer(this.getFilePath()).streamMove(move, Planner.getPlanner().getGame());
        } catch(TimeoutException e)
        {
            // timeout = out of time...you lose!
            System.err.println("Timeout!");
            System.err.println("Team [" + (this.getMyTeamIdx()+1) + " loses!");
            System.exit(-1);
        } catch(InterruptedException e)
        {
            e.printStackTrace();
            System.exit(-1);
        } catch(ExecutionException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }

        return move;
    }
}
