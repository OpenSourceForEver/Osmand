package net.osmand.plus.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.ArrayAdapter;

import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.QuadRect;
import net.osmand.data.RotatedTileBox;
import net.osmand.data.TransportStop;
import net.osmand.plus.ContextMenuAdapter;
import net.osmand.plus.ContextMenuAdapter.OnContextMenuClick;
import net.osmand.plus.R;
import net.osmand.plus.resources.TransportIndexRepository;

import java.util.ArrayList;
import java.util.List;

public class TransportStopsLayer extends OsmandMapLayer implements ContextMenuLayer.IContextMenuProvider {
	private static final int startZoom = 12;
	
	private Paint pointAltUI;
	private OsmandMapTileView view;
	private List<TransportStop> objects = new ArrayList<TransportStop>();
	private DisplayMetrics dm;
	
	
	@Override
	public void initLayer(OsmandMapTileView view) {
		this.view = view;
		dm = new DisplayMetrics();
		WindowManager wmgr = (WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE);
		wmgr.getDefaultDisplay().getMetrics(dm);

		pointAltUI = new Paint();
		pointAltUI.setColor(view.getResources().getColor(R.color.transport_stop));
		pointAltUI.setAntiAlias(true);
	}
	
	public void getFromPoint(RotatedTileBox tb,PointF point, List<? super TransportStop> res) {
		if (objects != null) {
			int ex = (int) point.x;
			int ey = (int) point.y;
			final int rp = getRadiusPoi(tb);
			int radius = rp * 3 / 2;
			int small = rp;
			try {
				for (int i = 0; i < objects.size(); i++) {
					TransportStop n = objects.get(i);
					if (n.getLocation() == null){
						continue;
					}
					int x = (int) tb.getPixXFromLatLon(n.getLocation().getLatitude(), n.getLocation().getLongitude());
					int y = (int) tb.getPixYFromLatLon(n.getLocation().getLatitude(), n.getLocation().getLongitude());
					if (Math.abs(x - ex) <= radius && Math.abs(y - ey) <= radius) {
						radius = small;
						res.add(n);
					}
				}
			} catch (IndexOutOfBoundsException e) {
				// that's really rare case, but is much efficient than introduce synchronized block
			}
		}
	}
	



	private String getStopDescription(TransportStop n, boolean useName) {
		StringBuilder text = new StringBuilder(250);
		text.append(view.getContext().getString(R.string.transport_Stop))
				.append(" : ").append(n.getName(view.getSettings().MAP_PREFERRED_LOCALE.get())); //$NON-NLS-1$
		text.append("\n").append(view.getContext().getString(R.string.transport_Routes)).append(" : "); //$NON-NLS-1$ //$NON-NLS-2$
		List<TransportIndexRepository> reps = view.getApplication().getResourceManager().searchTransportRepositories(
				n.getLocation().getLatitude(), n.getLocation().getLongitude());

		for (TransportIndexRepository t : reps) {
			if (t.acceptTransportStop(n)) {
				List<String> l;
				if (!useName) {
					l = t.getRouteDescriptionsForStop(n, "{1} {0}"); //$NON-NLS-1$
				} else if (view.getSettings().usingEnglishNames()) {
					l = t.getRouteDescriptionsForStop(n, "{1} {0} - {3}"); //$NON-NLS-1$
				} else {
					l = t.getRouteDescriptionsForStop(n, "{1} {0} - {2}"); //$NON-NLS-1$
				}
				if (l != null) {
					for (String s : l) {
						text.append("\n").append(s); //$NON-NLS-1$
					}
				}
			}
		}
		return text.toString();
	}
	
	public int getRadiusPoi(RotatedTileBox tb){
		final double zoom = tb.getZoom();
		int r;
		if(zoom < startZoom){
			r = 0;
		} else if(zoom <= 15){
			r = 8;
		} else if(zoom <= 16){
			r = 10;
		} else if(zoom <= 17){
			r = 14;
		} else {
			r = 18;
		}
		return (int) (r * tb.getDensity());
	}

	
	@Override
	public void onPrepareBufferImage(Canvas canvas, RotatedTileBox tb,
			DrawSettings settings) {
		if (tb.getZoom() >= startZoom) {
			objects.clear();
			final QuadRect latLonBounds = tb.getLatLonBounds();
			view.getApplication().getResourceManager().searchTransportAsync(latLonBounds.top, latLonBounds.left,
					latLonBounds.bottom, latLonBounds.right, tb.getZoom(), objects);
			int r = 3 * getRadiusPoi(tb) / 4;
			for (TransportStop o : objects) {
				int x = tb.getPixXFromLonNoRot(o.getLocation().getLongitude());
				int y = tb.getPixYFromLatNoRot(o.getLocation().getLatitude());
				canvas.drawRect(x - r, y - r, x + r, y + r, pointAltUI);
			}
		}
	}
	
	@Override
	public void onDraw(Canvas canvas, RotatedTileBox tb, DrawSettings settings) {
	}

	@Override
	public void destroyLayer() {
	}

	@Override
	public boolean drawInScreenPixels() {
		return false;
	}

	@Override
	public boolean onLongPressEvent(PointF point, RotatedTileBox tileBox) {
		return false;
	}

	@Override
	public String getObjectDescription(Object o) {
		if(o instanceof TransportStop){
			return getStopDescription((TransportStop) o, false);
		}
		return null;
	}
	
	private void showDescriptionDialog(TransportStop a) {
		AlertDialog.Builder bs = new AlertDialog.Builder(view.getContext());
		bs.setTitle(a.getName(view.getSettings().MAP_PREFERRED_LOCALE.get()));
		bs.setMessage(getStopDescription(a, true));
		bs.show();
	}
	
	@Override
	public PointDescription getObjectName(Object o) {
		if(o instanceof TransportStop){
			return new PointDescription(PointDescription.POINT_TYPE_POI, view.getContext().getString(R.string.transport_Stop),
					((TransportStop)o).getName()); 
		}
		return null;
	}

	@Override
	public boolean disableSingleTap() {
		return false;
	}

	@Override
	public boolean disableLongPressOnMap() {
		return false;
	}

	@Override
	public void collectObjectsFromPoint(PointF point, RotatedTileBox tileBox, List<Object> res) {
		getFromPoint(tileBox, point, res);
	}

	@Override
	public LatLon getObjectLocation(Object o) {
		if(o instanceof TransportStop){
			return ((TransportStop)o).getLocation();
		}
		return null;
	}
	
	@Override
	public void populateObjectContextMenu(Object o, ContextMenuAdapter adapter) {
		if(o instanceof TransportStop){
			final TransportStop a = (TransportStop) o;
			OnContextMenuClick listener = new ContextMenuAdapter.OnContextMenuClick() {
				@Override
				public boolean onContextMenuClick(ArrayAdapter<?> adapter, int itemId, int pos, boolean isChecked) {
					showDescriptionDialog(a);
					return true;
				}
			};
			adapter.item(R.string.poi_context_menu_showdescription)
			.iconColor( R.drawable.ic_action_note_dark).listen(listener).reg();
		}
	}



}
