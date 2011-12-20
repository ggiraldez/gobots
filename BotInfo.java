package gobots;


public class BotInfo {
    private static final int VELOCITY_SAMPLES = 20;

    private String name;
    private GUtil.MovingAverage avgVelocity;
    private boolean alive;

    public BotInfo(String name) {
        this.name = name;
        avgVelocity = new GUtil.MovingAverage(VELOCITY_SAMPLES);
        alive = true;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public String getName() {
        return name;
    }

    public void addVelocity(double velocity) {
        avgVelocity.addSample(velocity);
    }
    
    public double getAverageVelocity() {
        return avgVelocity.getAverage();
    }
}

