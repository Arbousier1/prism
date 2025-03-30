package network.darkhelmet.prism.events;

import network.darkhelmet.prism.api.BlockStateChange;
import network.darkhelmet.prism.api.PrismParameters;
import network.darkhelmet.prism.api.objects.ApplierResult;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class PrismRollBackEvent extends Event {


    private static final HandlerList handlers = new HandlerList();
    private final List<BlockStateChange> blockStateChanges;
    private final Player onBehalfOf;
    private final PrismParameters parameters;
    private final ApplierResult result;

    /**
     * Constructor.
     *
     * @param blockStateChanges List BlockStateChange
     * @param onBehalfOf        Player
     * @param parameters        QueryParameters
     * @param result            ApplierResult
     */
    protected PrismRollBackEvent(List<BlockStateChange> blockStateChanges, Player onBehalfOf,
                                 PrismParameters parameters, ApplierResult result) {
        this.blockStateChanges = blockStateChanges;
        this.onBehalfOf = onBehalfOf;
        this.parameters = parameters;
        this.result = result;
    }

    /**
     * Required by bukkit for proper event handling.
     */
    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() {
        return handlers;

    }

    /**
     * ArrayList.
     *
     * @return the originalBlock  List BlockStateChange
     */
    public List<BlockStateChange> getBlockStateChanges() {
        return blockStateChanges;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public ApplierResult getResult() {
        return result;
    }

    public Player getOnBehalfOf() {
        return onBehalfOf;
    }

    public PrismParameters getParameters() {
        return parameters;
    }
}
