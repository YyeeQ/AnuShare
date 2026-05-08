package moderation.strategy;

/**
 * Factory for ReportStrategy instances. Translates the strategy-name string
 * accepted by the moderation API into a concrete strategy object.
 */
public class ReportStrategyFactory {

    private ReportStrategyFactory() {}

    /**
     * @param name "OLDEST" or "MOST"
     * @throws IllegalArgumentException if name is null or unrecognised
     */
    public static ReportStrategy create(String name) {
        if (name == null) throw new IllegalArgumentException("strategy name is null");
        switch (name) {
            case "OLDEST": return new OldestStrategy();
            case "MOST":   return new MostStrategy();
            default:
                throw new IllegalArgumentException("unknown strategy: " + name);
        }
    }
}