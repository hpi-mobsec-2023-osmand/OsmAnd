package net.osmand.plus.quickaction;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import net.osmand.plus.ColorUtilities;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.dialogs.SelectMapViewQuickActionsBottomSheet;
import net.osmand.plus.views.controls.ReorderItemTouchHelperCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static net.osmand.AndroidUtils.isLayoutRtl;

public abstract class SwitchableAction<T> extends QuickAction {

	public static final String KEY_ID = "id";

	protected static final String KEY_DIALOG = "dialog";

	private transient EditText title;

	private transient Adapter adapter;
	private transient ItemTouchHelper touchHelper;

	protected SwitchableAction(QuickActionType type) {
		super(type);
	}

	public SwitchableAction(QuickAction quickAction) {
		super(quickAction);
	}

	@Override
	public void setAutoGeneratedTitle(EditText title) {
		this.title = title;
	}

	@Override
	public void drawUI(ViewGroup parent, final MapActivity activity) {
		View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.quick_action_switchable_action, parent, false);

		final SwitchCompat showDialog = view.findViewById(R.id.saveButton);
		if (!getParams().isEmpty()) {
			showDialog.setChecked(Boolean.valueOf(getParams().get(KEY_DIALOG)));
		}
		view.findViewById(R.id.saveButtonContainer).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				boolean selected = showDialog.isChecked();
				showDialog.setChecked(!selected);
			}
		});

		RecyclerView list = view.findViewById(R.id.list);
		adapter = new Adapter(activity, new QuickActionListFragment.OnStartDragListener() {
			@Override
			public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
				touchHelper.startDrag(viewHolder);
			}
		});

		ReorderItemTouchHelperCallback touchHelperCallback = new ReorderItemTouchHelperCallback(adapter);
		touchHelper = new ItemTouchHelper(touchHelperCallback);
		touchHelper.attachToRecyclerView(list);

		if (!getParams().isEmpty()) {
			adapter.addItems(loadListFromParams());
		}

		list.setAdapter(adapter);

		TextView dscrTitle = view.findViewById(R.id.textDscrTitle);
		TextView dscrHint = view.findViewById(R.id.textDscrHint);
		Button addBtn = view.findViewById(R.id.btnAdd);

		dscrTitle.setText(parent.getContext().getString(getDiscrTitle()) + ":");
		dscrHint.setText(getDiscrHint());
		addBtn.setText(getAddBtnText());
		addBtn.setOnClickListener(getOnAddBtnClickListener(activity, adapter));

		parent.addView(view);
	}

	@Override
	public String getActionText(OsmandApplication app) {
		String arrowDirection = isLayoutRtl(app) ? "\u25c0" : "\u25b6";

		List<QuickAction> actions = app.getQuickActionRegistry().collectQuickActionsByType(getActionType());
		if (actions.size() > 1) {
			String item = getNextSelectedItem(app);
			return "\u2026" + arrowDirection + getTranslatedItemName(app, item);
		} else {
			String item = getSelectedItem(app);
			return getTranslatedItemName(app, item) + arrowDirection + "\u2026";
		}
	}

	@Override
	public boolean fillParams(View root, MapActivity activity) {
		final RecyclerView list = root.findViewById(R.id.list);
		final Adapter adapter = (Adapter) list.getAdapter();

		boolean hasParams = adapter.itemsList != null && !adapter.itemsList.isEmpty();

		if (hasParams) saveListToParams(adapter.itemsList);

		return hasParams;
	}

	protected Adapter getAdapter() {
		return adapter;
	}

	public abstract List<T> loadListFromParams();

	public abstract void executeWithParams(MapActivity activity, String params);

	public abstract String getTranslatedItemName(Context context, String item);

	public abstract String getSelectedItem(OsmandApplication app);

	public abstract String getNextSelectedItem(OsmandApplication app);

	protected void showChooseDialog(FragmentManager fm) {
		SelectMapViewQuickActionsBottomSheet fragment = new SelectMapViewQuickActionsBottomSheet();
		Bundle args = new Bundle();
		args.putLong(KEY_ID, id);
		fragment.setArguments(args);
		fragment.show(fm, SelectMapViewQuickActionsBottomSheet.TAG);
	}

	protected class Adapter extends RecyclerView.Adapter<Adapter.ItemHolder> implements ReorderItemTouchHelperCallback.OnItemMoveCallback {

		private List<T> itemsList = new ArrayList<>();
		private final QuickActionListFragment.OnStartDragListener onStartDragListener;
		private final Context context;

		public Adapter(Context context, QuickActionListFragment.OnStartDragListener onStartDragListener) {
			this.context = context;
			this.onStartDragListener = onStartDragListener;
			this.itemsList = new ArrayList<>();
		}

		@Override
		public Adapter.ItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			return new Adapter.ItemHolder(inflater.inflate(R.layout.quick_action_switchable_item, parent, false));
		}

		@Override
		public void onBindViewHolder(final Adapter.ItemHolder holder, final int position) {
			final T item = itemsList.get(position);

			OsmandApplication app = (OsmandApplication) context.getApplicationContext();

			Drawable icon = app.getUIUtilities().getPaintedIcon(
					getItemIconRes(app, item), getItemIconColor(app, item));
			holder.icon.setImageDrawable(icon);

			holder.title.setText(getItemName(context, item));

			holder.handleView.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					if (event.getActionMasked() ==
							MotionEvent.ACTION_DOWN) {
						onStartDragListener.onStartDrag(holder);
					}
					return false;
				}
			});

			holder.closeBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {

					String oldTitle = getTitle(itemsList);
					String defaultName = holder.handleView.getContext().getString(getNameRes());

					deleteItem(holder.getAdapterPosition());

					if (oldTitle.equals(title.getText().toString()) || title.getText().toString().equals(defaultName)) {

						String newTitle = getTitle(itemsList);
						title.setText(newTitle);
					}
				}
			});
		}

		@Override
		public int getItemCount() {
			return itemsList.size();
		}

		public List<T> getItemsList() {
			return itemsList;
		}

		public void deleteItem(int position) {

			if (position == -1) {
				return;
			}

			itemsList.remove(position);
			notifyItemRemoved(position);
		}

		public void addItems(List<T> data) {

			if (!itemsList.containsAll(data)) {

				itemsList.addAll(data);
				notifyDataSetChanged();
			}
		}

		public void addItem(T item, Context context) {

			if (!itemsList.contains(item)) {

				String oldTitle = getTitle(itemsList);
				String defaultName = context.getString(getNameRes());

				int oldSize = itemsList.size();
				itemsList.add(item);

				notifyItemRangeInserted(oldSize, itemsList.size() - oldSize);

				if (oldTitle.equals(title.getText().toString()) || title.getText().toString().equals(defaultName)) {

					String newTitle = getTitle(itemsList);
					title.setText(newTitle);
				}
			}
		}

		@Override
		public boolean onItemMove(int selectedPosition, int targetPosition) {
			String oldTitle = getTitle(itemsList);
			String defaultName = context.getString(getNameRes());

			Collections.swap(itemsList, selectedPosition, targetPosition);
			if (selectedPosition - targetPosition < -1) {

				notifyItemMoved(selectedPosition, targetPosition);
				notifyItemMoved(targetPosition - 1, selectedPosition);

			} else if (selectedPosition - targetPosition > 1) {

				notifyItemMoved(selectedPosition, targetPosition);
				notifyItemMoved(targetPosition + 1, selectedPosition);

			} else {

				notifyItemMoved(selectedPosition, targetPosition);
			}

			notifyItemChanged(selectedPosition);
			notifyItemChanged(targetPosition);

			if (oldTitle.equals(title.getText().toString()) || title.getText().toString().equals(defaultName)) {

				String newTitle = getTitle(itemsList);
				title.setText(newTitle);
			}

			return true;
		}

		@Override
		public void onItemDismiss(RecyclerView.ViewHolder holder) {

		}

		public class ItemHolder extends RecyclerView.ViewHolder {
			public TextView title;
			public ImageView handleView;
			public ImageView closeBtn;
			public ImageView icon;

			public ItemHolder(View itemView) {
				super(itemView);

				title = itemView.findViewById(R.id.title);
				handleView = itemView.findViewById(R.id.handle_view);
				closeBtn = itemView.findViewById(R.id.closeImageButton);
				icon = itemView.findViewById(R.id.imageView);
			}
		}
	}

	protected abstract String getTitle(List<T> filters);

	protected abstract void saveListToParams(List<T> list);

	protected abstract String getItemName(Context context, T item);

	@DrawableRes
	protected int getItemIconRes(Context context, T item) {
		return R.drawable.ic_map;
	}

	@ColorInt
	protected int getItemIconColor(OsmandApplication app, T item) {
		boolean nightMode = !app.getSettings().isLightContent();
		int colorRes = ColorUtilities.getDefaultIconColorId(nightMode);
		return ContextCompat.getColor(app, colorRes);
	}

	protected abstract
	@StringRes
	int getAddBtnText();

	protected abstract
	@StringRes
	int getDiscrHint();

	protected abstract
	@StringRes
	int getDiscrTitle();

	protected abstract String getListKey();

	protected abstract View.OnClickListener getOnAddBtnClickListener(MapActivity activity, final Adapter adapter);

	protected void onItemsSelected(Context ctx, List<T> selectedItems) {

	}
}
