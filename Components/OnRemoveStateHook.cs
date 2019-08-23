using UnityEngine.UIElements;

namespace Arcadia
{
    public class OnRemoveStateHook : ArcadiaBehaviour
    {
        // TODO: optimize
        public void OnRemoveState(object key)
        {
            if (!_fullyInitialized)
                FullInit();
            HookStateSystem.SetState(arcadiaState, _go, ifnInfos);
            int i = 0;
            try {
                for (; i < ifnInfos.Length; i++) {
                    HookStateSystem.ifnInfoIndex = i;
                    if (ifnInfos[i].key == key)
                    {
                        Arcadia.Util.AsIFn(ifnInfos[i].fn).invoke(_go, ifnInfos[i].key);
                        break;
                        // key will be unique
                    }
                }
            } catch (System.Exception) {
                PrintContext(i);
                throw;
            } finally {
                HookStateSystem.Clear();
            }
        }       
    }
}