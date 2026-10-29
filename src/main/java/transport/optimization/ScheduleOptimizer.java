package transport.optimization;

public interface ScheduleOptimizer {

  OptimizationResult optimize(ProblemInstance problem);

  String name();
}
