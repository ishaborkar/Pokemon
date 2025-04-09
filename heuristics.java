package src.pas.pokemon.agents.pokemon.agents;

import java.util.ArrayList;

import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;

//import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.enums.Flag;
//import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
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

import edu.bu.pas.pokemon.utils.Pair;

//import src.pas.pokemon.agents.Node;


public class heuristics {

    public double evaluateUtility(Node node) {
        // Retrieve the Pokémon data
        //System.out.println("Evaluating utility...");
        Pokemon.PokemonView pokemon = node.getMyTeamView().getActivePokemonView();
        Pokemon.PokemonView opponent = node.getMyOpponentTeamView().getActivePokemonView();
    
        // Get the base stats of the Pokémon (e.g., attack, defense, etc.)
        int baseAttack = pokemon.getBaseStat(Stat.ATK);
        int baseDefense = pokemon.getBaseStat(Stat.DEF);
        int baseSpeed = pokemon.getBaseStat(Stat.SPD);
    
        // Apply stat stage multipliers using the provided getStatMultiplier method
        double attackMultiplier = getStatMultiplier(pokemon, Stat.ATK);
        double defenseMultiplier = getStatMultiplier(pokemon, Stat.DEF);
        double speedMultiplier = getStatMultiplier(pokemon, Stat.SPD);
    
        // Adjust stats based on stage multipliers
        double adjustedAttack = (baseAttack * attackMultiplier);
        double adjustedDefense = (baseDefense * defenseMultiplier);
        double adjustedSpeed = (baseSpeed * speedMultiplier);
    
        // Apply penalties for persistent statuses (non-volatile statuses)
        double statusPenalty = getNonVolatileStatusPenalty(pokemon);
    
        // Calculate total score based on stats and status effects
        double score = (adjustedAttack * 0.4) + (adjustedDefense * 0.3) + (adjustedSpeed * 0.2);
        score -= statusPenalty;
    
        double myHP = pokemon.getCurrentStat(Stat.HP);
        double oppHP = opponent.getCurrentStat(Stat.HP);
        double hpDiff = myHP - oppHP;
        score += (hpDiff * 0.5);
        double hp = myHP / pokemon.getBaseStat(Stat.HP);
    
        // Healing prioritization based on HP
        if (hp < .25) {
            score += prioritizeHealing(pokemon);
        } else if (hp < .5 && hp > .25) {
            score += prioritizeHealing(pokemon) * 0.5;
        }
    
        // Knockout and buffing bonus
        if (oppHP / opponent.getBaseStat(Stat.HP) < 0.2) {
            score += 5.0;
        }
        if (attackMultiplier > 1.0) {
            score += 1.5;
        }
        if (defenseMultiplier > 1.0) {
            score += 1.5;
        }
    
        // Type advantage bonus
        double typeAdvantage = getTypeAdvantage(pokemon, opponent) * 2;
        score += typeAdvantage;
    
        // Iterate through each move and categorize
        for (Move.MoveView move : pokemon.getMoveViews()) {
            if (move == null) continue;
    
            if (move.getCategory() == Move.Category.PHYSICAL) {
                score += evaluatePhysicalMove(move, adjustedAttack, opponent);
            } else if (move.getCategory() == Move.Category.SPECIAL) {
                score += evaluateSpecialMove(move, adjustedAttack, opponent);
            } else if (move.getCategory() == Move.Category.STATUS) {
                score += evaluateStatusMove(move, pokemon, myHP);
            }
        }
    
        return score;
    }
    
    private double evaluatePhysicalMove(Move.MoveView move, double adjustedAttack, Pokemon.PokemonView opponent) {
        double score = 0.0;
        double physicalDamage = adjustedAttack * move.getPower();
    
        // Check effectiveness
        if (Type.isSuperEffective(move.getType(), opponent.getCurrentType1()) ||
            Type.isSuperEffective(move.getType(), opponent.getCurrentType2())) {
            physicalDamage *= 2.0;
            score += physicalDamage * 1.5;
        } else {
            score += physicalDamage;
        }
    
        // KO potential bonus
        if (physicalDamage >= opponent.getCurrentStat(Stat.HP)) {
            score += 10.0;
        }
    
        // Priority move bonus
        if (move.getPriority() > 0) {
            score += move.getPriority() * 2.0;
        }
    
        return score;
    }
    private double evaluateSpecialMove(Move.MoveView move, double adjustedAttack, Pokemon.PokemonView opponent) {
        double score = 0.0;
        double specialDamage = adjustedAttack * move.getPower();
    
        // Check effectiveness
        if (Type.isSuperEffective(move.getType(), opponent.getCurrentType1()) ||
            Type.isSuperEffective(move.getType(), opponent.getCurrentType2())) {
            specialDamage *= 2.0;
            score += specialDamage * 1.5;
        } else {
            score += specialDamage;
        }
    
        // KO potential bonus
        if (specialDamage >= opponent.getCurrentStat(Stat.HP)) {
            score += 10.0;
        }
    
        // Priority move bonus
        if (move.getPriority() > 0) {
            score += move.getPriority() * 2.0;
        }
    
        return score;
    }

    // Helper method to calculate status penalties for non-volatile (persistent) statuses
public double getNonVolatileStatusPenalty(Pokemon.PokemonView pokemon) {
    NonVolatileStatus status = pokemon.getNonVolatileStatus();  // Get current persistent status
    //int statusDuration = pokemon.getNonVolatileStatusCounter(status);  // Get the duration of the current status

    if (status != NonVolatileStatus.NONE) {
        switch (status) {
            case SLEEP:
                return 2.0;  // Penalize heavily for sleep (unable to act)
            case PARALYSIS:
                double paralysisPenalty = 1.5;  // Heavier penalty for paralysis
                if (pokemon.getCurrentStat(Stat.SPD) < pokemon.getBaseStat(Stat.SPD)) {
                    paralysisPenalty += 0.5;  // If speed is reduced, add additional penalty
                }
                return paralysisPenalty;
            case POISON:
                return 0.2;  // Small penalty for poison (damage over time)
            case BURN:
                return 0.3;  // Small penalty for burn (damage over time)
            case TOXIC:
                return 0.4;  // Higher penalty for toxic (damage increases over time)
            case FREEZE:
                return 1.0;  // Penalize heavily for being frozen (unable to act)
            default:
                return 0.0;  // Default case (no penalty)
        } 
    } else {
        return 0.0;
    }
    
    
}

    private double evaluateStatusMove(Move.MoveView move, Pokemon.PokemonView pokemon, double myHP) {
        double score = 0.0;
        double statusScore = 0.0;
    
        // Buff moves
        if (move.getName().equals("Swords Dance") || move.getName().equals("Calm Mind") ||
            move.getName().equals("Bulk Up") || move.getName().equals("Iron Defense")) {
            statusScore += 5.0;
        }
    
        // Debuff moves
        if (move.getName().equals("Growl") || move.getName().equals("Leer") ||
            move.getName().equals("String Shot") || move.getName().equals("Sand Attack")) {
            statusScore += 3.0;
        }
    
        // Status-inflicting moves
        if (move.getName().equals("Toxic") || move.getName().equals("Will-O-Wisp") ||
            move.getName().equals("Thunder Wave") || move.getName().equals("Sleep Powder")) {
            statusScore += 6.0;
        }
    
        // Healing moves
        if (isHealingMove(move)) {
            double hpRatio = myHP / pokemon.getBaseStat(Stat.HP);
            if (hpRatio < 0.25) {
                statusScore += 8.0;
            } else if (hpRatio < 0.5) {
                statusScore += 4.0;
            } else {
                statusScore += 2.0;
            }
        }
    
        // Add the status move score to the total score
        score += statusScore;
        return score;
    }       


// Helper method for calculating type advantage (super-effective moves, etc.)
public double getTypeAdvantage(Pokemon.PokemonView pokemon, Pokemon.PokemonView opponent) {
    double typeAdvantage = 0;
    if (pokemon == null || opponent == null) {
        return 0;
    }
    Type enemyType1 = opponent.getCurrentType1();
    Type enemyType2 = opponent.getCurrentType2();

    
    if (enemyType1 == null && enemyType2 == null) {
        return 0;  
    }

    
    if (pokemon.getMoveViews() == null) {
        return 0;
    }

    for (Move.MoveView move : pokemon.getMoveViews()) {
        if (move == null) {
            continue; 
        }

        Type moveType = move.getType();
        if (moveType == null) {
            continue;  
        }

        if (enemyType1 != null && Type.isSuperEffective(moveType, enemyType1)) {
            typeAdvantage += 1.0;
        }
        if (enemyType2 != null && Type.isSuperEffective(moveType, enemyType2)) {
            typeAdvantage += 1.0;
        }
    }

    return typeAdvantage;
}

// Use the provided getStatMultiplier method to get stat multipliers
public double getStatMultiplier(Pokemon.PokemonView pokemon, Stat stat) {
    return pokemon.getStatMultiplier(stat); // Directly call the method to retrieve the stage multiplier
}

public double getUtility(Node node) {
    return evaluateUtility(node);
}

public boolean isHealingMove(Move.MoveView move) {
    String[] healingMoves = {"Recover", "Rest", "Soft-Boiled", "Synthesis", "Drain Punch", "Giga Drain", "Leech Life", "Absorb", "Mega Drain"};
    for (String healingMove : healingMoves) {
        if (move.getName().equals(healingMove)) {
            return true;
        }
    }
    return false;
}


public double prioritizeHealing(Pokemon.PokemonView pokemon) {
    double healing = 0.0;
    String [] potentialHealingMoves = {"Absorb", "Leech Life", "Recover", "Rest", "Soft-Boiled", "Mega Drain", "Dream Eater"};
    for (Move.MoveView move : pokemon.getMoveViews()) {
        for (String healingMove : potentialHealingMoves) {
            if (move != null && healingMove != null && move.getName().equals(healingMove)) {
                healing = 1.0;  // Heavily prioritize healing moves
                return healing;
            }
        }
    }
    return healing;

}



}