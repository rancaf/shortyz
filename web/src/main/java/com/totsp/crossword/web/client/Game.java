/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.totsp.crossword.web.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.http.client.Request;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.ClosingEvent;
import com.google.gwt.user.client.Window.ClosingHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.KeyboardListener;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import com.google.inject.Inject;

import com.totsp.crossword.puz.Playboard;
import com.totsp.crossword.puz.Playboard.Clue;
import com.totsp.crossword.puz.Playboard.Position;
import com.totsp.crossword.puz.Playboard.Word;
import com.totsp.crossword.puz.Puzzle;
import com.totsp.crossword.web.client.Renderer.ClickListener;
import com.totsp.crossword.web.client.resources.Css;
import com.totsp.crossword.web.client.resources.Resources;
import com.totsp.crossword.web.shared.PuzzleDescriptor;
import com.totsp.gwittir.client.fx.ui.SoftScrollArea;

import java.util.Arrays;
import java.util.HashMap;


/**
 *
 * @author kebernet
 */
public class Game {
    private static final WASDCodes CODES = GWT.create(WASDCodes.class);
    private static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private SoftScrollArea ssa = new SoftScrollArea();
    private boolean dirty;
    private Css css;
    private DisplayChangeListener displayChangeListener = new DisplayChangeListener() {
            @Override
            public void onDisplayChange() {
                ; //noop
            }
        };

    private FocusPanel mainPanel = new FocusPanel();
    private HandlerRegistration closingRegistration = null;
    private HashMap<Clue, Widget> acrossClueViews = new HashMap<Clue, Widget>();
    private HashMap<Clue, Widget> downClueViews = new HashMap<Clue, Widget>();
    private Playboard board;
    private KeyboardListener l = new KeyboardListener() {
            @Override
            public void onKeyPress(Widget sender, char keyCode, int modifiers) {
            }

            @Override
            public void onKeyDown(Widget sender, char keyCode, int modifiers) {
                if (board == null) {
                    return;
                }

                if ((keyCode == KeyCodes.KEY_DOWN) ||
                        (((modifiers & KeyboardListener.MODIFIER_CTRL) > 0) &&
                        (keyCode == CODES.s()))) {
                    Word w = board.moveDown();
                    render(w);

                    return;
                }

                if ((keyCode == KeyCodes.KEY_UP) ||
                        (((modifiers & KeyboardListener.MODIFIER_CTRL) > 0) &&
                        (keyCode == CODES.w()))) {
                    Word w = board.movieUp();
                    render(w);

                    return;
                }

                if ((keyCode == KeyCodes.KEY_LEFT) ||
                        (((modifiers & KeyboardListener.MODIFIER_CTRL) > 0) &&
                        (keyCode == CODES.a()))) {
                    Word w = board.moveLeft();
                    render(w);

                    return;
                }

                if ((keyCode == KeyCodes.KEY_RIGHT) ||
                        (((modifiers & KeyboardListener.MODIFIER_CTRL) > 0) &&
                        (keyCode == CODES.d()))) {
                    Word w = board.moveRight();
                    render(w);

                    return;
                }

                
                if ((keyCode == ' ') || (keyCode == KeyCodes.KEY_ENTER)) {
                    Word w = board.setHighlightLetter(board.getHighlightLetter());
                    render(w);

                    return;
                }

                if ((keyCode == KeyCodes.KEY_BACKSPACE) ||
                        (keyCode == KeyCodes.KEY_DELETE)) {
                    Word w = board.deleteLetter();
                    render(w);
                    dirty = true;

                    return;
                }

                if (((modifiers & KeyboardListener.MODIFIER_CTRL) == 0) &&
                        (ALPHA.indexOf(Character.toUpperCase(keyCode)) != -1)) {
                    Word w = board.playLetter(Character.toUpperCase(keyCode));
                    render(w);
                    dirty = true;

                    return;
                }
            }

            @Override
            public void onKeyUp(Widget sender, char keyCode, int modifiers) {
            }
        };

    private Label status = new Label();
    private PuzzleListView plv;
    private PuzzleServiceProxy service;
    private Renderer renderer;
    private Request request = null;
    private ScrollPanel acrossScroll = new ScrollPanel();
    private ScrollPanel downScroll = new ScrollPanel();
    private TextArea keyboardIntercept = new TextArea();
    private Timer autoSaveTimer = null;
    private VerticalPanel verticalPanel = new VerticalPanel();
    private Widget lastClueWidget;
    private Clue[] acrossClues;
    private Clue[] downClues;
    private boolean smallView;
    private Label clueLine = new Label();
    private FlexTable t;
    private HorizontalPanel display = new HorizontalPanel();

    @Inject
    public Game(RootPanel rootPanel, PuzzleServiceProxy service,
        Resources resources, final PuzzleListView plv, Renderer renderer) {
        this.service = service;
        this.plv = plv;
        this.renderer = renderer;
        this.css = resources.css();

        History.newItem("list", false);
        History.addValueChangeHandler(new ValueChangeHandler<String>() {
                @Override
                public void onValueChange(ValueChangeEvent<String> event) {
                    if (event.getValue().equals("list")) {
                        if (closingRegistration != null) {
                            closingRegistration.removeHandler();
                            closingRegistration = null;
                        }

                        if (autoSaveTimer != null) {
                            autoSaveTimer.cancel();
                            autoSaveTimer.run();
                            autoSaveTimer = null;
                        }

                        mainPanel.setWidget(plv);
                        keyboardIntercept.removeKeyboardListener(l);

                        getDisplayChangeListener().onDisplayChange();
                    } else if (event.getValue().startsWith("play=")) {
                        Long id = Long.parseLong(event.getValue().split("=")[1]);
                        loadPuzzle(id);
                    }
                }
            });
        verticalPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        verticalPanel.setWidth("100%");
        StyleInjector.inject(resources.css().getText());
        keyboardIntercept.setWidth("1px");
        keyboardIntercept.setHeight("1px");
        keyboardIntercept.setStyleName(css.keyboardIntercept());
        rootPanel.add(keyboardIntercept);
        
        verticalPanel.add(status);
        verticalPanel.setCellHorizontalAlignment(status,
            HasHorizontalAlignment.ALIGN_CENTER);
        verticalPanel.add(mainPanel);
        verticalPanel.setCellHorizontalAlignment(mainPanel,
            HasHorizontalAlignment.ALIGN_CENTER);
        display.add(verticalPanel);
        display.setWidth("97%");
        rootPanel.add(display);
       
    }

    public HorizontalPanel getDisplay(){
        return this.display;
    }

    public void loadList(){
        status.setText("Loading puzzles...");
        status.setStyleName(css.statusInfo());
        service.listPuzzles(new AsyncCallback<PuzzleDescriptor[]>() {
                @Override
                public void onFailure(Throwable caught) {
                    Window.alert("Failed to load puzzles. \n" +
                        caught.toString());
                }

                @Override
                public void onSuccess(PuzzleDescriptor[] result) {
                    Arrays.sort(result);
                    mainPanel.setWidget(plv);
                    plv.setValue(Arrays.asList(result));
                    setFBSize();
                    status.setStyleName(css.statusHidden());
                    status.setText(" ");
                }
            });
    }


    /**
     * @param displayChangeListener the displayChangeListener to set
     */
    public void setDisplayChangeListener(
        DisplayChangeListener displayChangeListener) {
        this.displayChangeListener = displayChangeListener;
    }

    /**
     * @return the displayChangeListener
     */
    public DisplayChangeListener getDisplayChangeListener() {
        return displayChangeListener;
    }

    public void loadPuzzle(final Long id) {
        status.setText("Loading puzzle...");
        status.setStyleName(css.statusInfo());

        if (request == null) {
            request = service.findPuzzle(id,
                    new AsyncCallback<Puzzle>() {
                        @Override
                        public void onFailure(Throwable caught) {
                            Window.alert(caught.toString());
                        }

                        @Override
                        public void onSuccess(Puzzle result) {
                            History.newItem("play=" + id, false);
                            startPuzzle(id, result);
                            keyboardIntercept.addKeyboardListener(l);
                            request = null;
                            status.setStyleName(css.statusHidden());
                            status.setText(" ");
                        }
                    });

            renderer.setClickListener(new ClickListener() {
                    @Override
                    public void onClick(int across, int down) {
                        Word w = board.setHighlightLetter(new Position(across,
                                    down));
                        render(w);
                        keyboardIntercept.setFocus(true);
                    }
                });
        } else {
            request.cancel();
            request = null;
            loadPuzzle(id);
        }
    }

    private static native void setFBSize() /*-{
    if($wnd.FB && $wnd.FB.CanvasClient.setSizeToContent){
    $wnd.FB.CanvasClient.setSizeToContent();
    }
    }-*/;

    private void render(Word w) {
        renderer.render(w);

        Position highlight = board.getHighlightLetter();
        this.ensureVisible((highlight.across +1) * 26, (highlight.down + 1) * 26);
        this.ensureVisible(highlight.across * 26, highlight.down * 26);
        
        
        if (lastClueWidget != null) {
            lastClueWidget.removeStyleName(css.highlightClue());
        }
        Clue clue = board.getClue();
        if (board.isAcross() && (board.getClue() != null)) {

            lastClueWidget = acrossClueViews.get(clue);
            acrossScroll.ensureVisible(lastClueWidget);
            clueLine.setText("(across) "+clue.number+". "+clue.hint);
        } else if (board.getClue() != null) {
            lastClueWidget = downClueViews.get(board.getClue());
            downScroll.ensureVisible(lastClueWidget);
            clueLine.setText("(down) "+clue.number+". "+clue.hint);
        }

        lastClueWidget.addStyleName(css.highlightClue());
        keyboardIntercept.setFocus(true);

    }

    private void ensureVisible(int x, int y){
        int maxScrollX = ssa.getMaxHorizontalScrollPosition();
    	int maxScrollY = ssa.getMaxScrollPosition() ;


    	int currentMinX = ssa.getHorizontalScrollPosition();
    	int currentMaxX = ssa.getOffsetWidth() + currentMinX;
    	int currentMinY = ssa.getScrollPosition();
    	int currentMaxY = ssa.getOffsetHeight() + currentMinY;

    	GWT.log("X range "+currentMinX+" to "+currentMaxX, null);
    	GWT.log("Desired X:"+x, null);
    	GWT.log("Y range "+currentMinY+" to "+currentMaxY, null);
    	GWT.log("Desired Y:"+y, null);

    	int newScrollX = ssa.getHorizontalScrollPosition();
        int newScrollY = ssa.getScrollPosition();
    	if( x < currentMinX || x > currentMaxX ){
    		newScrollX = x > maxScrollX ? maxScrollX : x;
    	}
    	if( y < currentMinY || y > currentMaxY ){
    		newScrollY = y > maxScrollY ? maxScrollY : y;
    	}
        ssa.setHorizontalScrollPosition(newScrollX);
        ssa.setScrollPosition(newScrollY);

    }

    private void render() {
        render(null);
    }

    private void startPuzzle(final long listingId, final Puzzle puzzle) {

        VerticalPanel outer = new VerticalPanel();
        board = new Playboard(puzzle);
        board.setResponder(this.getResponder());
        board.setHighlightLetter(new Position(0, 0));
        if(board.getBoxes()[0][0] == null ){
            board.moveRight();
        }
        if(this.isSmallView() ){
            outer.add(this.clueLine);
            outer.setCellHorizontalAlignment(this.clueLine, HasHorizontalAlignment.ALIGN_CENTER);
            this.clueLine.setStyleName(css.clueLine());
        }

        
        ssa.setWidth("425px");
        ssa.setHeight("425px");
        ssa.addMouseListener(ssa.MOUSE_MOVE_SCROLL_LISTENER);
        t = renderer.initialize(board);

        HorizontalPanel hp = new HorizontalPanel();
        ssa.setWidget(t);
        hp.add(ssa);

        acrossScroll.setWidth("155px");
        downScroll.setWidth("155px");

        VerticalPanel list = new VerticalPanel();

        acrossScroll.setWidget(list);

        int index = 0;

        for (Clue c : acrossClues = board.getAcrossClues()) {
            final int cIndex = index;
            Grid g = new Grid(1, 2);
            g.setWidget(0, 0, new Label(c.number + ""));
            g.getCellFormatter().setStyleName(0, 0, css.clueNumber());
            g.getRowFormatter()
             .setVerticalAlign(0, HasVerticalAlignment.ALIGN_TOP);
            g.setWidget(0, 1, new Label(c.hint));
            g.setStyleName(css.clueBox());

            list.add(g);
            acrossClueViews.put(c, g);
            g.addClickHandler(new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent event) {
                        board.jumpTo(cIndex, true);
                        mainPanel.setFocus(true);
                        render();
                    }
                });
            index++;
        }

        acrossScroll.setWidget(list);

        list = new VerticalPanel();

        downScroll.setWidget(list);
        index = 0;

        for (Clue c : downClues = board.getDownClues()) {
            final int cIndex = index;
            Grid g = new Grid(1, 2);
            g.setWidget(0, 0, new Label(c.number + ""));
            g.getCellFormatter().setStyleName(0, 0, css.clueNumber());
            g.getRowFormatter()
             .setVerticalAlign(0, HasVerticalAlignment.ALIGN_TOP);
            g.setWidget(0, 1, new Label(c.hint));
            g.setStyleName(css.clueBox());

            list.add(g);
            downClueViews.put(c, g);
            g.addClickHandler(new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent event) {
                        board.jumpTo(cIndex, false);
                        mainPanel.setFocus(true);
                        render();
                    }
                });
            index++;
        }

        downScroll.setWidget(list);

        if(!this.isSmallView()){
            hp.add(acrossScroll);
            hp.add(downScroll);
        }

        render(board.getCurrentWord());
        outer.add(hp);

        outer.setCellHorizontalAlignment(hp, HasHorizontalAlignment.ALIGN_CENTER);

        HorizontalPanel controls = new HorizontalPanel();
        Button back = new Button("Return to List",
                new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent event) {
                        History.newItem("list");
                    }
                });
        if(!this.isSmallView()){
            back.getElement().getStyle().setMarginRight(30, Unit.PX);
        }
        controls.add(back);

        controls.add(new Button("Show Errors",
                new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    board.toggleShowErrors();
                    ((Button) event.getSource()).setText(board.isShowErrors()
                            ? "Hide Errors" : "Show Errors");
                    render();
                }
            }));
        controls.add(new Button("Reveal Letter",
                new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    board.revealLetter();
                    dirty = true;
                    render();
                }
            }));
        controls.add(new Button("Reveal Word",
                new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    board.revealWord();
                    dirty = true;
                    render();
                }
            }));
        controls.add(new Button("Reveal Puzzle",
                new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    board.revealPuzzle();
                    dirty = true;
                    render();
                }
            }));
        controls.getElement().getStyle().setMarginTop(10, Unit.PX);
        outer.add(controls);

        mainPanel.setWidget(outer);

        keyboardIntercept.setFocus(true);

        acrossScroll.setHeight(t.getOffsetHeight() + "px ");
        downScroll.setHeight(t.getOffsetHeight() + "px ");
        setFBSize();

        autoSaveTimer = new Timer() {
                    @Override
                    public void run() {
                        if (!dirty) {
                            return;
                        }

                        dirty = false;
                        status.setStyleName(css.statusInfo());
                        status.setText("Autosaving...");
                        service.savePuzzle(listingId, puzzle,
                            new AsyncCallback() {
                                @Override
                                public void onFailure(Throwable caught) {
                                    GWT.log("Save failed", caught);
                                    status.setStyleName(css.statusError());
                                    status.setText("Failed to save puzzle.");
                                }

                                @Override
                                public void onSuccess(Object result) {
                                    status.setStyleName(css.statusHidden());
                                    status.setText(" ");
                                }
                            });
                    }
                };
        autoSaveTimer.scheduleRepeating(2 * 60 * 1000);
        closingRegistration = Window.addWindowClosingHandler(new ClosingHandler() {
                    @Override
                    public void onWindowClosing(ClosingEvent event) {
                        if (dirty) {
                            event.setMessage("Abandon unsaved changes?");
                        }
                    }
                });
        getDisplayChangeListener().onDisplayChange();
    }

    /**
     * @return the smallView
     */
    public boolean isSmallView() {
        return smallView;
    }

    /**
     * @param smallView the smallView to set
     */
    public void setSmallView(boolean smallView) {
        this.smallView = smallView;
    }

    public static interface DisplayChangeListener {
        public void onDisplayChange();
    }


    private String responder;

    /**
     * Get the value of responder
     *
     * @return the value of responder
     */
    public String getResponder() {
        return this.responder;
    }

    /**
     * Set the value of responder
     *
     * @param newresponder new value of responder
     */
    public void setResponder(String newresponder) {
        this.responder = newresponder;
    }

}
