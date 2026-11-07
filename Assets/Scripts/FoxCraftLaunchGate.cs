// A single, testable readiness rule shared by QuestCraft's menu and Pojlib
// download progress. UI handlers must never independently enable Play.
// This reports UI readiness, not Minecraft phone-renderer availability.
public static class FoxCraftLaunchGate
{
    public static bool CanPlay(bool authenticated, bool downloadsReady,
        bool launchInProgress)
    {
        return authenticated && downloadsReady && !launchInProgress;
    }

    public static bool CanManageInstances(bool authenticated, bool demoAccount,
        bool downloadsReady, bool launchInProgress)
    {
        return CanPlay(authenticated, downloadsReady, launchInProgress)
            && !demoAccount;
    }
}
