package transport.optimization;

public interface CostFunction {

  double evaluate(Schedule schedule, ProblemInstance problem);
}
