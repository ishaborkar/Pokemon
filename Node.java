package src.pas.pokemon.agents.pokemon.agents;

import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.enums.Flag;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Target;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Team;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.callbacks.MultiCallbackCallback;
import edu.bu.pas.pokemon.core.callbacks.ResetLastDamageDealtCallback;
import edu.bu.pas.pokemon.core.callbacks.DoDamageCallback;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.utils.Pair;
import edu.bu.pas.pokemon.core.enums.Stat;


//import src.pas.pokemon.agents.heuristics;



import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Locale.Category;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class Node {
    //adding the fields that might be needed
    int NodeType; //1 is a max node, 2 is a min node, and 3 is a chance node
    public static final int MAX_NODE = 1;
    public static final int MIN_NODE = 2;
    public static final int CHANCE_NODE = 3;
    
    private final List<Node> children;
    private final double probability; //chance nodes
    private final BattleView battleView;
    private final int depth;
    private final int myTeamIdx;
    private final int currentPlayerTeamIdx;

    private double utilityValue;
    private MoveView lastMoveView;

    public Node(int NodeType,List<Node> children,double probability, BattleView battleView, 
                int depth, int myTeamIdx,int currentPlayerTeamIdx){
        this.NodeType= NodeType;
        this.children = new ArrayList<Node>();
        //this.children = children;
        this.probability = probability;
        this.battleView=battleView;
        this.depth=depth;
        this.myTeamIdx=myTeamIdx;
        this.currentPlayerTeamIdx = currentPlayerTeamIdx;
        
    }

    public final BattleView getBattleView(){
        return this.battleView;
    }
    
    public final int getMyTeamIdx(){
        return this.myTeamIdx;
    }

    public final int getDepth(){
        return this.depth;
    }

    public final int getCurrentPlayerTeamIdx(){
        return this.currentPlayerTeamIdx;
    }

    public final double getProbability(){
        return this.probability;
    }
    
    public final double getUtilityValue() {
        return utilityValue;
    }

    public final MoveView getLastMoveView() {
        return lastMoveView;
    }

    public void setUtilityValue(double d){
        this.utilityValue = d;
    }

    public void setLastMoveView(Move.MoveView m){
        this.lastMoveView = m;
    }

    public final List<Node> getChildren() {
        return children;
    }

    public final int getMyOpponentTeamIdx(){
        if (this.getMyTeamIdx() == 0) {
            return 1;  // your opponent is team 1
        } else {
            return 0;  // your opponent is team 0
        }
    }

    public final int getOtherPlayerTeamIdx(){ //I don't know what this method is supposed to do
        if (this.getCurrentPlayerTeamIdx() == 0){
            return 1;
        } else{
            return 0;
        }
    }

    public final Team.TeamView getMyTeamView(){
        return battleView.getTeamView(myTeamIdx);
    }

    public final Team.TeamView getMyOpponentTeamView(){
        return battleView.getTeamView(getMyOpponentTeamIdx());
    }

    public final Team.TeamView getCurrentPlayerTeamView(){
        return battleView.getTeamView(getCurrentPlayerTeamIdx());
    }

    public final Team.TeamView getOtherPlayerTeamView(){
        return battleView.getTeamView(getOtherPlayerTeamIdx());
    }

    //Accounts for if pokemon faints, need to switch 
    private List<BattleView> faint(BattleView view, int myTeamIdx){
        List<BattleView> result = new ArrayList<>();
        boolean Ifainted = view.getTeamView(myTeamIdx).getActivePokemonView().hasFainted();
        boolean Oppfainted = view.getTeamView(this.getMyOpponentTeamIdx()).getActivePokemonView().hasFainted();

        if (!Ifainted && !Oppfainted){
            result.add(view);
            return result;
        }

        //will hold the indices of all the pokemon that can be switched into if pokemon faints 
        List<Integer> myUnfainted = new ArrayList<>();
        List<Integer> oppUnfainted = new ArrayList<>();

        //loop over all pokemon in each team that are alive and add them to list
        for (int i = 0; i < view.getTeamView(0).size(); ++i) {
            if (!view.getTeamView(0).getPokemonView(i).hasFainted()) {
                myUnfainted.add(i);
            }
        }
        for (int i = 0; i < view.getTeamView(1).size(); ++i) {
            if (!view.getTeamView(1).getPokemonView(i).hasFainted()) {
                oppUnfainted.add(i); 
            }
        }
        for(int i:myUnfainted){
            for(int j: oppUnfainted){
                Battle b= new Battle(view);
                b.getTeam(myTeamIdx).switchActivePokemonTo(i);
                b.getTeam(this.getMyOpponentTeamIdx()).switchActivePokemonTo(j);
                result.add(b.getView());
            }
        }
        return result;
    }

    public final boolean isTerminal(int maxDepth){ //now isTerminal will take in the max depth when we use it in the tree traversal
        if(this.getDepth()>= maxDepth || this.battleView.isOver()){
            return true;
        } else{
            return false;
        }
    }

    public double getHeuristic(){
        heuristics heuristic = new heuristics();
        //System.out.println("Heuristic value: " + heuristic.evaluateUtility(this));
        return heuristic.evaluateUtility(this);

    }

    public List<Pair<Double, List<MoveView>>> moveOrder(MoveView move, MoveView oppMove, PokemonView mypokemon, PokemonView opppokemon){
        List<Pair<Double, List<MoveView>>> order = new ArrayList<>();
        int myPriority = move.getPriority();
        int opponentPriority = oppMove.getPriority();

        double mySpeed = mypokemon.getCurrentStat(Stat.SPD);
        double opponentSpeed = opppokemon.getCurrentStat(Stat.SPD);

        if(mypokemon.getNonVolatileStatus()==NonVolatileStatus.PARALYSIS){
            mySpeed *= 0.75;
        }
        if (opppokemon.getNonVolatileStatus()==NonVolatileStatus.PARALYSIS){
            opponentSpeed*=0.75;
        }
        //double typeAdvantage = getTypeAdvantage(mypokemon, opppokemon);

        if (myPriority>opponentPriority){
            order.add(new Pair<>(1.0, Arrays.asList(move, oppMove)));
        } else if(opponentPriority>myPriority){
            order.add(new Pair<>(1.0, Arrays.asList(oppMove,move)));
        } else{
            if (mySpeed>opponentSpeed){
                order.add(new Pair<>(1.0, Arrays.asList(move, oppMove)));
            } else if(opponentSpeed>mySpeed){
                order.add(new Pair<>(1.0, Arrays.asList(oppMove,move)));
            } else{
                order.add(new Pair<>(0.5, Arrays.asList(oppMove,move)));
                order.add(new Pair<>(0.5, Arrays.asList(move,oppMove)));
            }
        }
        return order;

    }

    public List<Node> makeChildren(Move.MoveView moveView, int casterIdx, int oppIdx, int maxDepth){
        List<Node> children = new ArrayList<>();
        if (this.isTerminal(maxDepth)){
            return children;
        }
        if(this.NodeType == MAX_NODE){
            //our turn to choose a move
            Team.TeamView team = getMyTeamView(); //using getter methode created in node clas
            Move.MoveView[] possiblemoves = team.getActivePokemonView().getMoveViews(); //gets an array of moves that the current active pokemon can even make

            for (MoveView move: possiblemoves){
                //after max node, a chance node. Create a chance node below:
                Node childNode = new Node(CHANCE_NODE, new ArrayList<>(), 1.0, battleView, this.getDepth()+1, this.myTeamIdx, this.getMyOpponentTeamIdx());
                childNode.setLastMoveView(move); //tried to use setLastMove(Move m) from index.html but VS code instead autocorrected to this
                children.add(childNode);
            }
        } else if (this.NodeType == MIN_NODE){
            Team.TeamView oppteam = getMyOpponentTeamView();
            Move.MoveView[] possiblemoves = oppteam.getActivePokemonView().getMoveViews();
            for (MoveView move: possiblemoves){
                Node childNode = new Node(MAX_NODE, new ArrayList<>(), 1.0, battleView, this.getDepth()+1, this.myTeamIdx, this.getMyTeamIdx()); 
                //next move is MAX again if not chance, so last parament is also myteamidx
                childNode.setLastMoveView(move);
                children.add(childNode);
                
            }
        } else { //CHANCE NODE
            //chance nodes account for first if node is sleeping/paralyzed/frozen
            //if not, account for is the node is confused, and if node is confused, 50% chance that move will not happen

            if(getLastMoveView()==null|| moveView==null){
                return children;
            }

            PokemonView mypoke= this.getCurrentPlayerTeamView().getActivePokemonView();
            PokemonView opppoke = this.getMyOpponentTeamView().getActivePokemonView();

            List<Pair<Double, List<MoveView>>> order = moveOrder(moveView, getLastMoveView(), mypoke, opppoke);

            for(Pair<Double, List<MoveView>> o: order){
                double prob = o.getFirst();
                MoveView firstMove = o.getSecond().get(0);
                MoveView secondMove = o.getSecond().get(1);
                int firstTeamIdx;
                int secondTeamIdx;

                if (firstMove == getLastMoveView()) {
                    firstTeamIdx = getCurrentPlayerTeamIdx();
                    secondTeamIdx = getOtherPlayerTeamIdx();
                } else {
                    firstTeamIdx = getOtherPlayerTeamIdx();
                    secondTeamIdx = getCurrentPlayerTeamIdx();
                }
                
                List<Pair<Double, BattleView>> preMoveView = battleView.applyPreMoveConditions(firstTeamIdx); 
                for (Pair<Double, BattleView> p: preMoveView){
                    double probability = p.getFirst();
                    BattleView preMove = p.getSecond();
                    TeamView x = preMove.getTeamView(firstTeamIdx);
                    PokemonView caster = x.getActivePokemonView();
                    boolean isConfused = caster.getFlag(Flag.CONFUSED);

                    if(isConfused){
                        Move hurtYourselfMove = new Move(
                        "SelfDamage",
                        Type.NORMAL,
                        Move.Category.PHYSICAL,
                        40,
                        null,
                        Integer.MAX_VALUE,
                        1,
                        0
                        ).addCallback(
                            new MultiCallbackCallback(
                                new ResetLastDamageDealtCallback(), 
                                new DoDamageCallback(Target.CASTER, false, false, true) 
                            )
                        );
                        List<Pair<Double, BattleView> > confusionDamageOutcomes = hurtYourselfMove.getView().getPotentialEffects
                        (preMove, firstTeamIdx, secondTeamIdx);

                        for (Pair<Double, BattleView> i: confusionDamageOutcomes){
                            double pr=i.getFirst();
                            BattleView view = i.getSecond();
                            Battle copy = new Battle(view);
                            copy.applyPostTurnConditions();
                            List<BattleView> resolved = faint(copy.getView(), this.myTeamIdx);
                            for(BattleView b: resolved){
                                Node child = new Node(MIN_NODE, new ArrayList<>(), prob * pr * probability * 0.5, b, this.depth + 1, this.myTeamIdx, this.getMyOpponentTeamIdx());
                                children.add(child);
                            }
                            
                        }
                        List<Pair<Double, BattleView>> normalOutcomes = firstMove.getPotentialEffects(preMove, firstTeamIdx, secondTeamIdx);
                        for (Pair<Double, BattleView> outcome : normalOutcomes) {
                            double pr = outcome.getFirst();
                            BattleView view = outcome.getSecond();
                            Battle copy = new Battle(view);
                            copy.applyPostTurnConditions();
                            List<BattleView> resolved = faint(copy.getView(), this.myTeamIdx);
                            for(BattleView b: resolved){
                                Node child = new Node(MIN_NODE, new ArrayList<>(), prob * pr * probability * 0.5, b, this.depth + 1, this.myTeamIdx, this.getMyOpponentTeamIdx());
                                children.add(child);
                            }
                        }
                    } else{
                        List<Pair<Double, BattleView>> outcomes = firstMove.getPotentialEffects(preMove, firstTeamIdx, secondTeamIdx);
                        for (Pair<Double, BattleView> outcome : outcomes) {
                            double pr = outcome.getFirst();
                            BattleView view = outcome.getSecond();
                            Battle copy = new Battle(view);
                            copy.applyPostTurnConditions();
                            List<BattleView> resolved = faint(copy.getView(), this.myTeamIdx);
                            for(BattleView b: resolved){
                                Node child = new Node(MIN_NODE, new ArrayList<>(), prob * pr * probability * 1.0, b, this.depth + 1, this.myTeamIdx, this.getMyOpponentTeamIdx());
                                children.add(child);
                            }
                        }
                    }
                }
            }
        }
        return children;

    }
    


}