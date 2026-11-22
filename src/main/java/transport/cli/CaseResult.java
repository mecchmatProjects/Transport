package transport.cli;

/** Результат одного граничного випадку в демо-сценарії. */
record CaseResult(int number, String title, String expected, String actual, boolean ok) {}
